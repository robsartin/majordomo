package com.majordomo.adapter.in.web.librarian;

import com.majordomo.adapter.in.web.config.OrgContext;
import com.majordomo.domain.model.EntityNotFoundException;
import com.majordomo.domain.model.librarian.Book;
import com.majordomo.domain.model.librarian.BookFilter;
import com.majordomo.domain.model.librarian.BookStatus;
import com.majordomo.domain.model.librarian.Confidence;
import com.majordomo.domain.model.librarian.EnrichmentCandidate;
import com.majordomo.domain.port.in.librarian.CatalogBooksUseCase;
import com.majordomo.domain.port.in.librarian.ListBooksUseCase;
import com.majordomo.domain.port.in.librarian.ReviewEnrichmentUseCase;
import com.majordomo.domain.port.out.librarian.BookRepository;
import com.majordomo.domain.port.out.librarian.EnrichmentCandidateRepository;
import com.majordomo.domain.port.out.librarian.ShelfPhotoExtractionPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Thymeleaf controller for the Librarian tab: the catalog, a book's detail, and
 * the enrichment review queue.
 *
 * <p>Every read is scoped to the authenticated user's organization, and a book
 * belonging to another organization is a 404 rather than a 403 — the existence
 * of someone else's row is not ours to confirm.
 */
@Controller
public class LibrarianPageController {

    private static final Logger LOG = LoggerFactory.getLogger(LibrarianPageController.class);

    private static final int DEFAULT_LIMIT = 100;
    private static final int REVIEW_LIMIT = 50;

    private final ListBooksUseCase listBooks;
    private final CatalogBooksUseCase catalog;
    private final ShelfPhotoExtractionPort shelfPhotos;
    private final ReviewEnrichmentUseCase review;
    private final BookRepository books;
    private final EnrichmentCandidateRepository candidates;

    /**
     * A queue row: the proposed match together with the book it is proposed for,
     * so the reviewer can compare them without a second lookup.
     *
     * @param candidate the proposed external match
     * @param book      the book it was proposed for, or null if it has gone
     */
    public record ReviewRow(EnrichmentCandidate candidate, Book book) { }

    /**
     * Constructs the controller.
     *
     * @param listBooks   inbound port for catalog listing
     * @param catalog     inbound port for importing rows
     * @param shelfPhotos outbound port for reading a shelf photograph
     * @param review      inbound port for the review queue
     * @param books       book repository, for detail lookups
     * @param candidates  candidate repository, for a book's proposed matches
     */
    public LibrarianPageController(ListBooksUseCase listBooks,
                                   CatalogBooksUseCase catalog,
                                   ShelfPhotoExtractionPort shelfPhotos,
                                   ReviewEnrichmentUseCase review,
                                   BookRepository books,
                                   EnrichmentCandidateRepository candidates) {
        this.listBooks = listBooks;
        this.catalog = catalog;
        this.shelfPhotos = shelfPhotos;
        this.review = review;
        this.books = books;
        this.candidates = candidates;
    }

    /**
     * Renders the catalog, optionally narrowed.
     *
     * @param status     optional shelf-status filter
     * @param confidence optional confidence filter
     * @param q          optional title/author substring
     * @param orgContext the authenticated context
     * @param model      the Thymeleaf model
     * @return the {@code librarian} template name
     */
    @GetMapping("/librarian")
    public String librarian(@RequestParam(required = false) BookStatus status,
                            @RequestParam(required = false) Confidence confidence,
                            @RequestParam(required = false) String q,
                            OrgContext orgContext,
                            Model model) {
        var filter = new BookFilter(status, confidence, q);
        var page = listBooks.list(orgContext.organizationId(), filter, null, DEFAULT_LIMIT);

        model.addAttribute("books", page.items());
        model.addAttribute("hasMore", page.hasMore());
        model.addAttribute("filter", filter);
        model.addAttribute("statuses", BookStatus.values());
        model.addAttribute("confidences", Confidence.values());
        model.addAttribute("pendingReviewCount",
                review.pending(orgContext.organizationId(), null, REVIEW_LIMIT).items().size());
        return "librarian";
    }

    /**
     * Renders one book with any matches proposed for it.
     *
     * @param id         the book id
     * @param orgContext the authenticated context
     * @param model      the Thymeleaf model
     * @return the {@code librarian-book} template name
     */
    @GetMapping("/librarian/books/{id}")
    public String bookDetail(@PathVariable UUID id, OrgContext orgContext, Model model) {
        Book book = books.findById(id)
                .filter(b -> orgContext.organizationId().equals(b.getOrganizationId()))
                .orElseThrow(() -> new EntityNotFoundException("BOOK", id));

        model.addAttribute("book", book);
        model.addAttribute("candidates", candidates.findByBookId(id));
        return "librarian-book";
    }

    /**
     * Renders the queue of matches awaiting a decision.
     *
     * @param orgContext the authenticated context
     * @param model      the Thymeleaf model
     * @return the {@code librarian-review} template name
     */
    @GetMapping("/librarian/review")
    public String reviewQueue(OrgContext orgContext, Model model) {
        var pending = review.pending(orgContext.organizationId(), null, REVIEW_LIMIT);
        var rows = new ArrayList<ReviewRow>(pending.items().size());
        for (EnrichmentCandidate candidate : pending.items()) {
            rows.add(new ReviewRow(candidate, books.findById(candidate.bookId()).orElse(null)));
        }
        model.addAttribute("rows", List.copyOf(rows));
        model.addAttribute("hasMore", pending.hasMore());
        return "librarian-review";
    }

    /**
     * Applies a proposed match.
     *
     * @param candidateId the candidate to accept
     * @param orgContext  the authenticated context
     * @return a redirect back to the queue
     */
    @PostMapping("/librarian/review/{candidateId}/accept")
    public String accept(@PathVariable UUID candidateId, OrgContext orgContext) {
        review.accept(candidateId, orgContext.organizationId());
        return "redirect:/librarian/review";
    }

    /**
     * Turns down a proposed match, leaving the book unchanged.
     *
     * @param candidateId the candidate to reject
     * @param orgContext  the authenticated context
     * @return a redirect back to the queue
     */
    @PostMapping("/librarian/review/{candidateId}/reject")
    public String reject(@PathVariable UUID candidateId, OrgContext orgContext) {
        review.reject(candidateId, orgContext.organizationId());
        return "redirect:/librarian/review";
    }

    /**
     * Reads an uploaded shelf photograph and imports what it finds.
     *
     * <p>Extracted rows go through the same importer as the CSV, so they dedupe
     * and upsert identically. They arrive marked {@code PHOTO_EXTRACTION}, which
     * caps them below HIGH confidence — nobody has checked them, so they belong
     * in the review queue rather than the catalog's trusted tier (ADR-0023).
     *
     * <p>A failed read is reported as a failed read. Degrading it to "no books
     * found" would be indistinguishable from a photograph of an empty shelf.
     *
     * @param photo      the uploaded image
     * @param orgContext the authenticated context
     * @param flash      redirect attributes carrying the outcome message
     * @return a redirect to the catalog
     */
    @PostMapping("/librarian/import/photo")
    public String importPhoto(@RequestParam("photo") MultipartFile photo,
                              OrgContext orgContext,
                              RedirectAttributes flash) {
        if (photo == null || photo.isEmpty()) {
            flash.addFlashAttribute("importError", "Choose a photograph to import.");
            return "redirect:/librarian";
        }
        String mediaType = photo.getContentType();
        if (mediaType == null || !mediaType.startsWith("image/")) {
            flash.addFlashAttribute("importError",
                    "That file is not an image (" + mediaType + ").");
            return "redirect:/librarian";
        }
        try {
            var rows = shelfPhotos.extract(
                    photo.getBytes(), mediaType, photo.getOriginalFilename());
            if (rows.isEmpty()) {
                flash.addFlashAttribute("importMessage",
                        "No books were legible in that photograph.");
                return "redirect:/librarian";
            }
            var imported = catalog.catalog(rows, orgContext.organizationId());
            flash.addFlashAttribute("importMessage",
                    imported.size() + " book(s) imported from the photograph. "
                            + "They are marked for review until you confirm them.");
        } catch (IOException e) {
            LOG.warn("Could not read uploaded shelf photo: {}", e.toString());
            flash.addFlashAttribute("importError", "That upload could not be read.");
        } catch (RuntimeException e) {
            LOG.warn("Shelf photo extraction failed: {}", e.toString());
            flash.addFlashAttribute("importError",
                    "The photograph could not be read. The catalog is unchanged.");
        }
        return "redirect:/librarian";
    }
}
