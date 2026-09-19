package com.majordomo.application.librarian;

import com.majordomo.IntegrationTest;
import com.majordomo.adapter.in.ingest.librarian.BookCsvReader;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.identity.Organization;
import com.majordomo.domain.model.librarian.Book;
import com.majordomo.domain.model.librarian.BookFilter;
import com.majordomo.domain.model.librarian.BookImportRow;
import com.majordomo.domain.model.librarian.Confidence;
import com.majordomo.domain.port.in.librarian.CatalogBooksUseCase;
import com.majordomo.domain.port.out.identity.OrganizationRepository;
import com.majordomo.domain.port.out.librarian.BookRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Imports the real 57-row shelf end to end, against real PostgreSQL. This is
 * the test that would catch an importer that satisfied "idempotent" by skipping
 * rows it had already seen: the row count alone cannot tell the two apart, so
 * a changed rating is asserted explicitly.
 */
@IntegrationTest
class ShelfImportIntegrationTest {

    private static final Path SEED = Path.of("doc/librarian/bookshelf-2026-09-18.csv");

    @Autowired
    private CatalogBooksUseCase catalog;

    @Autowired
    private BookRepository books;

    @Autowired
    private OrganizationRepository organizations;

    private UUID newOrg() {
        UUID id = UuidFactory.newId();
        organizations.save(new Organization(id, "org-" + id));
        return id;
    }

    private List<BookImportRow> seedRows() throws IOException {
        try (var reader = Files.newBufferedReader(SEED)) {
            return new BookCsvReader().read(reader);
        }
    }

    private List<Book> allBooks(UUID orgId) {
        return books.findByOrganization(orgId, BookFilter.none(), null, 100).items();
    }

    @Test
    void import_catalogsAllFiftySevenBooksFromTheRealShelf() throws IOException {
        UUID orgId = newOrg();

        catalog.catalog(seedRows(), orgId);

        assertThat(allBooks(orgId)).hasSize(57);
    }

    @Test
    void import_gradesConfidenceFromTheTranscriptionNotes() throws IOException {
        UUID orgId = newOrg();
        catalog.catalog(seedRows(), orgId);

        Map<Confidence, Long> byConfidence = allBooks(orgId).stream()
                .collect(Collectors.groupingBy(Book::getConfidence, Collectors.counting()));

        assertThat(byConfidence).containsOnly(
                Map.entry(Confidence.HIGH, 33L),
                Map.entry(Confidence.MEDIUM, 8L),
                Map.entry(Confidence.LOW, 16L));
    }

    @Test
    void reimport_leavesFiftySevenBooksRatherThanOneHundredAndFourteen() throws IOException {
        UUID orgId = newOrg();
        var rows = seedRows();

        catalog.catalog(rows, orgId);
        catalog.catalog(rows, orgId);

        assertThat(allBooks(orgId)).hasSize(57);
    }

    @Test
    void reimport_appliesAChangedRatingRatherThanSkippingTheRow() throws IOException {
        UUID orgId = newOrg();
        var rows = seedRows();
        catalog.catalog(rows, orgId);

        // Re-import the same shelf with one rating changed.
        BookImportRow first = rows.getFirst();
        int changed = first.rating() == 5 ? 3 : 5;
        var edited = rows.stream()
                .map(r -> r.equals(first)
                        ? new BookImportRow(r.title(), r.author(), r.photo(), r.notes(), changed)
                        : r)
                .toList();
        catalog.catalog(edited, orgId);

        Map<String, Book> byTitle = allBooks(orgId).stream()
                .collect(Collectors.toMap(Book::getTitle, Function.identity()));
        assertThat(byTitle.get(first.title()).getRating()).isEqualTo(changed);
        assertThat(allBooks(orgId)).hasSize(57);
    }

    @Test
    void reimport_doesNotUndoAReviewedEnrichmentResult() throws IOException {
        UUID orgId = newOrg();
        var rows = seedRows();
        catalog.catalog(rows, orgId);

        Book enriched = allBooks(orgId).getFirst();
        enriched.setWikidataQid("Q7000573");
        enriched.setIsbn13("9780198805090");
        books.save(enriched);

        catalog.catalog(rows, orgId);

        Book after = books.findById(enriched.getId()).orElseThrow();
        assertThat(after.getWikidataQid()).isEqualTo("Q7000573");
        assertThat(after.getIsbn13()).isEqualTo("9780198805090");
    }
}
