package com.majordomo.application.librarian;

import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.event.BookCataloged;
import com.majordomo.domain.model.librarian.Book;
import com.majordomo.domain.model.librarian.BookImportRow;
import com.majordomo.domain.model.librarian.BookKeys;
import com.majordomo.domain.model.librarian.BookStatus;
import com.majordomo.domain.model.librarian.Confidence;
import com.majordomo.domain.port.in.librarian.CatalogBooksUseCase;
import com.majordomo.domain.port.out.EventPublisher;
import com.majordomo.domain.port.out.librarian.BookRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Imports transcribed shelf rows into the catalog.
 *
 * <p><strong>Import upserts; it does not skip.</strong> Until the web UI ships,
 * the CSV is the only way to record a rating, a shelf location or a status
 * change, so an importer that ignored rows it had already seen would silently
 * discard every edit the owner made (ADR-0023). A re-import therefore updates
 * the owner-authored fields and leaves enrichment results alone, since those
 * are owned by the review queue rather than the spreadsheet.
 */
@Service
public class BookCatalogService implements CatalogBooksUseCase {

    /** The transcriber did not read this — it was supplied or guessed. */
    private static final List<String> NOT_READ_MARKERS =
            List.of("filled from knowledge", "inferred", "uncertain", "not visible");

    /** Read, but imperfectly. */
    private static final List<String> PARTLY_READ_MARKERS =
            List.of("partly visible", "partly obscured", "partly legible", "obscured", "cut off");

    private final BookRepository books;
    private final EventPublisher events;

    /**
     * Constructs the service with required outbound ports.
     *
     * @param books  the book repository
     * @param events the domain event publisher
     */
    public BookCatalogService(BookRepository books, EventPublisher events) {
        this.books = books;
        this.events = events;
    }

    @Override
    @Transactional
    public List<Book> catalog(List<BookImportRow> rows, UUID organizationId) {
        var result = new ArrayList<Book>(rows.size());
        for (BookImportRow row : rows) {
            List<String> authors = BookKeys.splitAuthors(row.author());
            String key = BookKeys.normalize(row.title(), authors);

            var existing = books.findByNormalizedKey(key, organizationId);
            if (existing.isPresent()) {
                result.add(books.save(applyOwnerFields(existing.get(), row, authors)));
            } else {
                Book created = books.save(newBook(row, authors, key, organizationId));
                events.publish(new BookCataloged(
                        created.getId(), organizationId, created.getTitle(),
                        created.getSourcePhoto(), Instant.now()));
                result.add(created);
            }
        }
        return List.copyOf(result);
    }

    private Book newBook(BookImportRow row, List<String> authors, String key, UUID organizationId) {
        var book = new Book();
        book.setId(UuidFactory.newId());
        book.setOrganizationId(organizationId);
        book.setNormalizedKey(key);
        book.setStatus(BookStatus.OWNED);
        book.setCopies(1);
        return applyOwnerFields(book, row, authors);
    }

    /**
     * Copies the fields the owner authors in the spreadsheet. Enrichment results
     * — ISBN, publisher, Wikidata QID, Open Library key — are deliberately not
     * touched: a re-import must not undo a reviewed match.
     */
    private Book applyOwnerFields(Book book, BookImportRow row, List<String> authors) {
        book.setTitle(row.title());
        book.setAuthors(authors);
        book.setSourcePhoto(row.photo());
        book.setNotes(row.notes());
        book.setConfidence(confidenceFrom(row.notes()));
        if (row.rating() != null) {
            book.setRating(row.rating());
        }
        return book;
    }

    /**
     * Grades a row by what the transcription notes admit about it.
     *
     * <p>Three kinds of note appear on this shelf and they are not
     * interchangeable: the spine was not read at all ("filled from knowledge",
     * "title inferred"), it was read imperfectly ("partly visible"), or the note
     * is about something else entirely ("Vertical stack, left side", "Two copies
     * on shelf"). Only the first two say anything about whether the metadata can
     * be trusted, and the difference is what the review queue works from.
     */
    private Confidence confidenceFrom(String notes) {
        if (notes == null || notes.isBlank()) {
            return Confidence.HIGH;
        }
        String lower = notes.toLowerCase(Locale.ROOT);
        if (NOT_READ_MARKERS.stream().anyMatch(lower::contains)) {
            return Confidence.LOW;
        }
        if (PARTLY_READ_MARKERS.stream().anyMatch(lower::contains)) {
            return Confidence.MEDIUM;
        }
        // A note that says nothing about legibility does not downgrade the row.
        // Plenty of the shelf's notes are positional ("Vertical stack, left
        // side") or inventory ("Three copies on shelf"); treating any note at
        // all as a caveat would push five clean rows into the review queue.
        return Confidence.HIGH;
    }
}
