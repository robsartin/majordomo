package com.majordomo.adapter.out.persistence.librarian;

import com.majordomo.IntegrationTest;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.identity.Organization;
import com.majordomo.domain.model.librarian.Book;
import com.majordomo.domain.model.librarian.BookFilter;
import com.majordomo.domain.model.librarian.BookStatus;
import com.majordomo.domain.model.librarian.Confidence;
import com.majordomo.domain.port.out.identity.OrganizationRepository;
import com.majordomo.domain.port.out.librarian.BookRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Persistence behaviour for the book catalog against a real PostgreSQL instance
 * via Testcontainers. The array-valued authors column and the org-scoped unique
 * dedupe index are Postgres-specific, so the H2 slice cannot stand in here.
 * Every test scopes to a freshly-generated organization id so rows seeded
 * elsewhere cannot affect exact-count assertions.
 */
@IntegrationTest
class BookRepositoryIntegrationTest {

    @Autowired
    private BookRepository books;

    @Autowired
    private OrganizationRepository organizations;

    private UUID newOrg() {
        UUID id = UuidFactory.newId();
        organizations.save(new Organization(id, "org-" + id));
        return id;
    }

    private Book book(UUID orgId, String title, String author) {
        Book b = new Book();
        b.setId(UuidFactory.newId());
        b.setOrganizationId(orgId);
        b.setTitle(title);
        b.setAuthors(List.of(author));
        b.setNormalizedKey(title.toLowerCase() + "|" + author.toLowerCase());
        b.setStatus(BookStatus.OWNED);
        b.setConfidence(Confidence.HIGH);
        return b;
    }

    @Test
    void save_roundTripsEveryCatalogField() {
        UUID orgId = newOrg();
        Book b = book(orgId, "Networks (Second Edition)", "Mark Newman");
        b.setRating(4);
        b.setLocation("bookcase 1, shelf 2");
        b.setSourcePhoto("1");
        b.setWikidataQid("Q7000573");
        b.setCopies(1);
        b.setYear(2018);

        UUID id = books.save(b).getId();

        var found = books.findById(id).orElseThrow();
        assertThat(found.getTitle()).isEqualTo("Networks (Second Edition)");
        assertThat(found.getAuthors()).containsExactly("Mark Newman");
        assertThat(found.getRating()).isEqualTo(4);
        assertThat(found.getLocation()).isEqualTo("bookcase 1, shelf 2");
        assertThat(found.getStatus()).isEqualTo(BookStatus.OWNED);
        assertThat(found.getConfidence()).isEqualTo(Confidence.HIGH);
        assertThat(found.getWikidataQid()).isEqualTo("Q7000573");
        assertThat(found.getYear()).isEqualTo(2018);
        assertThat(found.getOrganizationId()).isEqualTo(orgId);
    }

    @Test
    void save_persistsUnratedBookWithNullRating() {
        UUID orgId = newOrg();
        Book saved = books.save(book(orgId, "Refactoring", "Martin Fowler"));

        assertThat(books.findById(saved.getId()).orElseThrow().getRating()).isNull();
    }

    @Test
    void findByNormalizedKey_locatesTheBookImportWouldUpdate() {
        UUID orgId = newOrg();
        books.save(book(orgId, "Clean Architecture", "Robert C. Martin"));

        var found = books.findByNormalizedKey("clean architecture|robert c. martin", orgId);

        assertThat(found).isPresent();
        assertThat(found.orElseThrow().getTitle()).isEqualTo("Clean Architecture");
    }

    @Test
    void findByNormalizedKey_doesNotLeakAcrossOrganizations() {
        UUID mine = newOrg();
        UUID theirs = newOrg();
        books.save(book(theirs, "Clean Architecture", "Robert C. Martin"));

        var found = books.findByNormalizedKey("clean architecture|robert c. martin", mine);

        assertThat(found).isEmpty();
    }

    @Test
    void findByOrganization_returnsOnlyThisOrgsBooks() {
        UUID mine = newOrg();
        UUID theirs = newOrg();
        books.save(book(mine, "Refactoring", "Martin Fowler"));
        books.save(book(theirs, "Small Worlds", "Duncan J. Watts"));

        var page = books.findByOrganization(mine, BookFilter.none(), null, 10);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().getTitle()).isEqualTo("Refactoring");
    }

    @Test
    void findByOrganization_pagesInIdOrderThroughCursor() {
        UUID orgId = newOrg();
        books.save(book(orgId, "First", "A Author"));
        books.save(book(orgId, "Second", "B Author"));
        books.save(book(orgId, "Third", "C Author"));

        var first = books.findByOrganization(orgId, BookFilter.none(), null, 2);
        assertThat(first.items()).hasSize(2);
        assertThat(first.hasMore()).isTrue();

        var second = books.findByOrganization(orgId, BookFilter.none(), first.nextCursor(), 2);
        assertThat(second.items()).hasSize(1);
        assertThat(second.hasMore()).isFalse();

        assertThat(first.items()).extracting(Book::getTitle).containsExactly("First", "Second");
        assertThat(second.items()).extracting(Book::getTitle).containsExactly("Third");
    }

    @Test
    void findByOrganization_omitsArchivedBooks() {
        UUID orgId = newOrg();
        Book archived = book(orgId, "Discarded", "Someone");
        archived.setArchivedAt(java.time.Instant.now());
        books.save(archived);
        books.save(book(orgId, "Kept", "Someone Else"));

        var page = books.findByOrganization(orgId, BookFilter.none(), null, 10);

        assertThat(page.items()).extracting(Book::getTitle).containsExactly("Kept");
    }

    @Test
    void save_rejectsASecondLiveBookWithTheSameNormalizedKey() {
        UUID orgId = newOrg();
        books.save(book(orgId, "Refactoring", "Martin Fowler"));

        assertThatThrownBy(() -> books.save(book(orgId, "Refactoring", "Martin Fowler")))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void save_allowsRecatalogingWhenThePreviousCopyIsArchived() {
        UUID orgId = newOrg();
        Book first = book(orgId, "Refactoring", "Martin Fowler");
        first.setArchivedAt(java.time.Instant.now());
        books.save(first);

        Book replacement = books.save(book(orgId, "Refactoring", "Martin Fowler"));

        assertThat(replacement.getId()).isNotEqualTo(first.getId());
    }

    @Test
    void findByOrganization_filtersByConfidenceSoDoubtfulRowsCanBeFound() {
        UUID orgId = newOrg();
        books.save(book(orgId, "Clean Read", "Someone"));
        Book doubtful = book(orgId, "Half Legible", "Someone Else");
        doubtful.setConfidence(Confidence.LOW);
        books.save(doubtful);

        var page = books.findByOrganization(
                orgId, new com.majordomo.domain.model.librarian.BookFilter(null, Confidence.LOW, null), null, 10);

        assertThat(page.items()).extracting(Book::getTitle).containsExactly("Half Legible");
    }

    @Test
    void findByOrganization_filtersByTitleSubstringCaseInsensitively() {
        UUID orgId = newOrg();
        books.save(book(orgId, "Networks (Second Edition)", "Mark Newman"));
        books.save(book(orgId, "Refactoring", "Martin Fowler"));

        var page = books.findByOrganization(
                orgId, new com.majordomo.domain.model.librarian.BookFilter(null, null, "NETWORKS"), null, 10);

        assertThat(page.items()).extracting(Book::getTitle).containsExactly("Networks (Second Edition)");
    }

    @Test
    void findByOrganization_filtersByAuthorThroughTheNormalisedKey() {
        UUID orgId = newOrg();
        books.save(book(orgId, "Networks (Second Edition)", "Mark Newman"));
        books.save(book(orgId, "Refactoring", "Martin Fowler"));

        var page = books.findByOrganization(
                orgId, new com.majordomo.domain.model.librarian.BookFilter(null, null, "fowler"), null, 10);

        assertThat(page.items()).extracting(Book::getTitle).containsExactly("Refactoring");
    }
}
