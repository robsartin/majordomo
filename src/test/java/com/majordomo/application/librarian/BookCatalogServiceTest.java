package com.majordomo.application.librarian;

import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.event.BookCataloged;
import com.majordomo.domain.model.librarian.Book;
import com.majordomo.domain.model.librarian.BookFilter;
import com.majordomo.domain.model.librarian.BookImportRow;
import com.majordomo.domain.model.librarian.ImportSource;
import com.majordomo.domain.model.librarian.BookStatus;
import com.majordomo.domain.model.librarian.Confidence;
import com.majordomo.domain.port.out.EventPublisher;
import com.majordomo.domain.port.out.librarian.BookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BookCatalogServiceTest {

    /** In-memory stand-in for the repository; real behaviour, no mock framework. */
    private static final class FakeBookRepository implements BookRepository {
        private final Map<UUID, Book> byId = new LinkedHashMap<>();

        @Override
        public Book save(Book book) {
            byId.put(book.getId(), book);
            return book;
        }

        @Override
        public Optional<Book> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<Book> findByNormalizedKey(String normalizedKey, UUID organizationId) {
            return byId.values().stream()
                    .filter(b -> organizationId.equals(b.getOrganizationId()))
                    .filter(b -> normalizedKey.equals(b.getNormalizedKey()))
                    .filter(b -> b.getArchivedAt() == null)
                    .findFirst();
        }

        @Override
        public Page<Book> findByOrganization(UUID organizationId, BookFilter filter, UUID cursor, int limit) {
            return new Page<>(List.copyOf(byId.values()), null, false);
        }

        int count() {
            return byId.size();
        }
    }

    private FakeBookRepository repository;
    private List<Object> published;
    private BookCatalogService service;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        repository = new FakeBookRepository();
        published = new ArrayList<>();
        EventPublisher publisher = published::add;
        service = new BookCatalogService(repository, publisher);
        orgId = UuidFactory.newId();
    }

    private BookImportRow row(String title, String author, String notes, Integer rating) {
        return new BookImportRow(title, author, "2", notes, rating, ImportSource.CSV);
    }

    private BookImportRow extractedRow(String title, String author, String notes) {
        return new BookImportRow(title, author, "2", notes, null, ImportSource.PHOTO_EXTRACTION);
    }

    @Test
    void catalog_insertsNewBooksAndSplitsAuthors() {
        service.catalog(List.of(row("Graph Algorithms", "Mark Needham & Amy Hodler", "", 5)), orgId);

        assertThat(repository.count()).isEqualTo(1);
        Book saved = repository.byId.values().iterator().next();
        assertThat(saved.getTitle()).isEqualTo("Graph Algorithms");
        assertThat(saved.getAuthors()).containsExactly("Mark Needham", "Amy Hodler");
        assertThat(saved.getRating()).isEqualTo(5);
        assertThat(saved.getStatus()).isEqualTo(BookStatus.OWNED);
        assertThat(saved.getOrganizationId()).isEqualTo(orgId);
        assertThat(saved.getNormalizedKey()).isNotBlank();
    }

    @Test
    void catalog_gradesACleanlyReadRowAsHighConfidence() {
        service.catalog(List.of(row("Refactoring", "Martin Fowler", "", 5)), orgId);

        assertThat(repository.byId.values().iterator().next().getConfidence())
                .isEqualTo(Confidence.HIGH);
    }

    @Test
    void catalog_gradesAnAuthorFilledFromKnowledgeAsLowConfidence() {
        service.catalog(List.of(
                row("Efficient C++", "Dov Bulka", "Author not visible in photo; filled from knowledge", 4)), orgId);

        assertThat(repository.byId.values().iterator().next().getConfidence())
                .isEqualTo(Confidence.LOW);
    }

    @Test
    void catalog_gradesAPartlyVisibleRowAsMediumConfidence() {
        service.catalog(List.of(row("Small Worlds", "Duncan J. Watts", "Bottom shelf, partly visible", 5)), orgId);

        assertThat(repository.byId.values().iterator().next().getConfidence())
                .isEqualTo(Confidence.MEDIUM);
    }

    @Test
    void catalog_keepsHighConfidenceWhenTheNoteIsPositionalRatherThanLegibility() {
        service.catalog(List.of(row("Seven Languages", "Bruce A. Tate", "Vertical stack, left side", 5)), orgId);

        assertThat(repository.byId.values().iterator().next().getConfidence())
                .isEqualTo(Confidence.HIGH);
    }

    @Test
    void catalog_keepsHighConfidenceWhenTheNoteRecordsCopyCount() {
        service.catalog(List.of(row("Refactoring", "Martin Fowler", "Three copies on shelf", 5)), orgId);

        assertThat(repository.byId.values().iterator().next().getConfidence())
                .isEqualTo(Confidence.HIGH);
    }

    @Test
    void catalog_gradesAnInferredTitleAsLowConfidence() {
        service.catalog(List.of(
                row("C++ Primer", "Stanley B. Lippman", "Only 'Lippman' legible; title inferred", 5)), orgId);

        assertThat(repository.byId.values().iterator().next().getConfidence())
                .isEqualTo(Confidence.LOW);
    }

    @Test
    void catalog_gradesAPartlyLegibleSpineAsMediumConfidence() {
        service.catalog(List.of(
                row("Accelerated C++", "Andrew Koenig", "Spine small; author partly legible", 5)), orgId);

        assertThat(repository.byId.values().iterator().next().getConfidence())
                .isEqualTo(Confidence.MEDIUM);
    }

    @Test
    void catalog_publishesBookCatalogedForEachNewBook() {
        service.catalog(List.of(row("Refactoring", "Martin Fowler", "", 5)), orgId);

        assertThat(published).hasSize(1);
        assertThat(published.getFirst()).isInstanceOf(BookCataloged.class);
        assertThat(((BookCataloged) published.getFirst()).title()).isEqualTo("Refactoring");
    }

    @Test
    void catalog_isIdempotentWhenTheSameShelfIsImportedTwice() {
        var rows = List.of(row("Refactoring", "Martin Fowler", "", 5));

        service.catalog(rows, orgId);
        service.catalog(rows, orgId);

        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void catalog_updatesRatingOnReimportRatherThanSkippingTheRow() {
        service.catalog(List.of(row("Refactoring", "Martin Fowler", "", 3)), orgId);
        service.catalog(List.of(row("Refactoring", "Martin Fowler", "", 5)), orgId);

        assertThat(repository.byId.values().iterator().next().getRating()).isEqualTo(5);
    }

    @Test
    void catalog_leavesAnExistingRatingAloneWhenTheColumnIsBlank() {
        service.catalog(List.of(row("Refactoring", "Martin Fowler", "", 4)), orgId);
        service.catalog(List.of(row("Refactoring", "Martin Fowler", "", null)), orgId);

        assertThat(repository.byId.values().iterator().next().getRating()).isEqualTo(4);
    }

    @Test
    void catalog_neverClobbersReviewedEnrichmentOnReimport() {
        service.catalog(List.of(row("Refactoring", "Martin Fowler", "", 4)), orgId);
        Book enriched = repository.byId.values().iterator().next();
        enriched.setWikidataQid("Q123456");
        enriched.setOpenLibraryKey("OL123M");
        enriched.setIsbn13("9780134757599");

        service.catalog(List.of(row("Refactoring", "Martin Fowler", "", 5)), orgId);

        Book after = repository.byId.values().iterator().next();
        assertThat(after.getWikidataQid()).isEqualTo("Q123456");
        assertThat(after.getOpenLibraryKey()).isEqualTo("OL123M");
        assertThat(after.getIsbn13()).isEqualTo("9780134757599");
        assertThat(after.getRating()).isEqualTo(5);
    }

    @Test
    void catalog_preservesIdentityOnReimport() {
        service.catalog(List.of(row("Refactoring", "Martin Fowler", "", 4)), orgId);
        UUID originalId = repository.byId.values().iterator().next().getId();

        service.catalog(List.of(row("Refactoring", "Martin Fowler", "", 5)), orgId);

        assertThat(repository.byId.values().iterator().next().getId()).isEqualTo(originalId);
    }

    @Test
    void catalog_doesNotRepublishBookCatalogedForAnUpdatedBook() {
        service.catalog(List.of(row("Refactoring", "Martin Fowler", "", 4)), orgId);
        published.clear();

        service.catalog(List.of(row("Refactoring", "Martin Fowler", "", 5)), orgId);

        assertThat(published).isEmpty();
    }

    @Test
    void catalog_scopesDedupeToTheImportingOrganization() {
        UUID otherOrg = UuidFactory.newId();
        service.catalog(List.of(row("Refactoring", "Martin Fowler", "", 4)), orgId);
        service.catalog(List.of(row("Refactoring", "Martin Fowler", "", 4)), otherOrg);

        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void catalog_neverGradesAPhotoExtractedRowAsHighConfidence() {
        service.catalog(List.of(extractedRow("Refactoring", "Martin Fowler", "")), orgId);

        assertThat(repository.byId.values().iterator().next().getConfidence())
                .isNotEqualTo(Confidence.HIGH)
                .isEqualTo(Confidence.MEDIUM);
    }

    @Test
    void catalog_keepsAPhotoExtractedRowLowWhenTheModelFlaggedDoubt() {
        service.catalog(List.of(extractedRow("C++ Primer", "Stanley Lippman", "title inferred")), orgId);

        assertThat(repository.byId.values().iterator().next().getConfidence())
                .isEqualTo(Confidence.LOW);
    }

    @Test
    void catalog_stillGradesACleanCsvRowAsHighConfidence() {
        service.catalog(List.of(row("Refactoring", "Martin Fowler", "", 5)), orgId);

        assertThat(repository.byId.values().iterator().next().getConfidence())
                .isEqualTo(Confidence.HIGH);
    }
}
