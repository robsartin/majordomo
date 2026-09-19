package com.majordomo.adapter.out.persistence.librarian;

import com.majordomo.IntegrationTest;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.identity.Organization;
import com.majordomo.domain.model.librarian.Book;
import com.majordomo.domain.model.librarian.BookStatus;
import com.majordomo.domain.model.librarian.Confidence;
import com.majordomo.domain.model.librarian.EnrichmentCandidate;
import com.majordomo.domain.port.out.identity.OrganizationRepository;
import com.majordomo.domain.port.out.librarian.BookRepository;
import com.majordomo.domain.port.out.librarian.EnrichmentCandidateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistence behaviour for proposed external matches, against real PostgreSQL.
 * The JSONB payload column and the partial pending index are Postgres-specific.
 */
@IntegrationTest
class EnrichmentCandidateRepositoryIntegrationTest {

    @Autowired
    private EnrichmentCandidateRepository candidates;

    @Autowired
    private BookRepository books;

    @Autowired
    private OrganizationRepository organizations;

    @Autowired
    private JpaEnrichmentCandidateRepository jpa;

    private UUID newOrg() {
        UUID id = UuidFactory.newId();
        organizations.save(new Organization(id, "org-" + id));
        return id;
    }

    private Book newBook(UUID orgId, String title) {
        Book b = new Book();
        b.setId(UuidFactory.newId());
        b.setOrganizationId(orgId);
        b.setTitle(title);
        b.setAuthors(List.of("Someone"));
        b.setNormalizedKey(title.toLowerCase() + "|someone");
        b.setStatus(BookStatus.OWNED);
        return books.save(b);
    }

    private EnrichmentCandidate candidate(UUID bookId, double score, Confidence confidence) {
        return new EnrichmentCandidate(
                UuidFactory.newId(), bookId, "OPEN_LIBRARY", "OL" + score,
                score, confidence, Map.of("publisher", "OUP"), Instant.now(), null, null);
    }

    @Test
    void save_roundTripsScoreConfidenceAndJsonbPayload() {
        UUID orgId = newOrg();
        Book book = newBook(orgId, "Networks");

        UUID id = candidates.save(candidate(book.getId(), 0.92, Confidence.HIGH)).id();

        var found = candidates.findById(id).orElseThrow();
        assertThat(found.bookId()).isEqualTo(book.getId());
        assertThat(found.source()).isEqualTo("OPEN_LIBRARY");
        assertThat(found.score()).isEqualTo(0.92);
        assertThat(found.confidence()).isEqualTo(Confidence.HIGH);
        assertThat(found.payload()).containsEntry("publisher", "OUP");
    }

    @Test
    void findByBookId_returnsEveryCandidateProposedForThatBook() {
        UUID orgId = newOrg();
        Book book = newBook(orgId, "Graph Algorithms");
        candidates.save(candidate(book.getId(), 0.91, Confidence.HIGH));
        candidates.save(candidate(book.getId(), 0.42, Confidence.LOW));

        assertThat(candidates.findByBookId(book.getId())).hasSize(2);
    }

    @Test
    void findPending_returnsCandidatesAwaitingAHumanDecision() {
        UUID orgId = newOrg();
        Book book = newBook(orgId, "Small Worlds");
        candidates.save(candidate(book.getId(), 0.55, Confidence.MEDIUM));

        var page = candidates.findPending(orgId, null, 10);

        assertThat(page.items()).hasSize(1);
    }

    @Test
    void findPending_excludesCandidatesAlreadyReviewed() {
        UUID orgId = newOrg();
        Book book = newBook(orgId, "Refactoring");
        UUID reviewedId = candidates.save(candidate(book.getId(), 0.61, Confidence.MEDIUM)).id();

        var entity = jpa.findById(reviewedId).orElseThrow();
        entity.setReviewedAt(Instant.now());
        entity.setAccepted(Boolean.TRUE);
        jpa.save(entity);

        assertThat(candidates.findPending(orgId, null, 10).items()).isEmpty();
    }

    @Test
    void findPending_doesNotLeakAcrossOrganizations() {
        UUID mine = newOrg();
        UUID theirs = newOrg();
        Book theirBook = newBook(theirs, "Clean Architecture");
        candidates.save(candidate(theirBook.getId(), 0.77, Confidence.MEDIUM));

        assertThat(candidates.findPending(mine, null, 10).items()).isEmpty();
    }
}
