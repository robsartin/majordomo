package com.majordomo.application.librarian;

import com.majordomo.domain.model.EntityNotFoundException;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.event.BookEnriched;
import com.majordomo.domain.model.librarian.Book;
import com.majordomo.domain.model.librarian.Confidence;
import com.majordomo.domain.model.librarian.EnrichmentCandidate;
import com.majordomo.domain.port.out.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.majordomo.application.librarian.LibrarianEnrichmentTestSupport.FakeBooks;
import static com.majordomo.application.librarian.LibrarianEnrichmentTestSupport.FakeCandidates;
import static com.majordomo.application.librarian.LibrarianEnrichmentTestSupport.book;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnrichmentReviewServiceTest {

    private FakeBooks books;
    private FakeCandidates candidates;
    private List<Object> published;
    private EnrichmentReviewService service;
    private UUID orgId;
    private Book subject;

    @BeforeEach
    void setUp() {
        books = new FakeBooks();
        candidates = new FakeCandidates();
        published = new ArrayList<>();
        EventPublisher publisher = published::add;
        service = new EnrichmentReviewService(books, candidates, publisher);
        orgId = UuidFactory.newId();
        subject = books.save(book(orgId, "Refactoring", "Martin Fowler"));
    }

    private EnrichmentCandidate pending() {
        var c = new EnrichmentCandidate(
                UuidFactory.newId(), subject.getId(), "OPEN_LIBRARY", "/works/OL1W",
                0.72, Confidence.MEDIUM,
                Map.of("publisher", "Addison-Wesley", "isbn13", "9780134757599", "year", "2018"),
                Instant.now(), null, null);
        return candidates.save(c);
    }

    @Test
    void pending_listsCandidatesAwaitingADecision() {
        pending();

        assertThat(service.pending(orgId, null, 10).items()).hasSize(1);
    }

    @Test
    void accept_appliesThePayloadToTheBook() {
        var c = pending();

        service.accept(c.id(), orgId);

        Book after = books.findById(subject.getId()).orElseThrow();
        assertThat(after.getPublisher()).isEqualTo("Addison-Wesley");
        assertThat(after.getIsbn13()).isEqualTo("9780134757599");
        assertThat(after.getYear()).isEqualTo(2018);
        assertThat(after.getOpenLibraryKey()).isEqualTo("/works/OL1W");
        assertThat(after.getConfidence()).isEqualTo(Confidence.HIGH);
    }

    @Test
    void accept_marksTheCandidateDecidedSoItLeavesTheQueue() {
        var c = pending();

        service.accept(c.id(), orgId);

        assertThat(service.pending(orgId, null, 10).items()).isEmpty();
        assertThat(candidates.findById(c.id()).orElseThrow().accepted()).isTrue();
    }

    @Test
    void accept_publishesBookEnriched() {
        var c = pending();

        service.accept(c.id(), orgId);

        assertThat(published).hasSize(1);
        assertThat(published.getFirst()).isInstanceOf(BookEnriched.class);
    }

    @Test
    void reject_leavesTheBookCompletelyUntouched() {
        var c = pending();

        service.reject(c.id(), orgId);

        Book after = books.findById(subject.getId()).orElseThrow();
        assertThat(after.getPublisher()).isNull();
        assertThat(after.getIsbn13()).isNull();
        assertThat(after.getOpenLibraryKey()).isNull();
        assertThat(published).isEmpty();
    }

    @Test
    void reject_keepsTheCandidateSoTheDecisionLeavesATrail() {
        var c = pending();

        service.reject(c.id(), orgId);

        var stored = candidates.findById(c.id()).orElseThrow();
        assertThat(stored.accepted()).isFalse();
        assertThat(stored.isPending()).isFalse();
        assertThat(stored.payload()).containsEntry("publisher", "Addison-Wesley");
    }

    @Test
    void accept_refusesACandidateBelongingToAnotherOrganization() {
        var c = pending();

        assertThatThrownBy(() -> service.accept(c.id(), UuidFactory.newId()))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void accept_refusesToDecideTheSameCandidateTwice() {
        var c = pending();
        service.accept(c.id(), orgId);

        assertThatThrownBy(() -> service.accept(c.id(), orgId))
                .isInstanceOf(IllegalStateException.class);
    }
}
