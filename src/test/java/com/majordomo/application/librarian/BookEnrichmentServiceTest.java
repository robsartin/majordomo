package com.majordomo.application.librarian;

import com.majordomo.domain.model.EntityNotFoundException;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.event.BookEnriched;
import com.majordomo.domain.model.librarian.Book;
import com.majordomo.domain.model.librarian.Confidence;
import com.majordomo.domain.model.librarian.EnrichmentCandidate;
import com.majordomo.domain.port.out.EventPublisher;
import com.majordomo.domain.port.out.librarian.BookMetadataSource;
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

class BookEnrichmentServiceTest {

    private FakeBooks books;
    private FakeCandidates candidates;
    private List<Object> published;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        books = new FakeBooks();
        candidates = new FakeCandidates();
        published = new ArrayList<>();
        orgId = UuidFactory.newId();
    }

    /** A source that returns whatever it is given, scored as stated. */
    private BookMetadataSource source(String name, double... scores) {
        return new BookMetadataSource() {
            @Override
            public String sourceName() {
                return name;
            }

            @Override
            public List<EnrichmentCandidate> findCandidates(Book b) {
                var out = new ArrayList<EnrichmentCandidate>();
                for (double s : scores) {
                    out.add(new EnrichmentCandidate(
                            UuidFactory.newId(), b.getId(), name, "EXT-" + s, s,
                            com.majordomo.domain.model.librarian.MatchScorer.confidenceFor(s),
                            Map.of("publisher", "OUP", "isbn13", "9780000000001", "year", "2018"),
                            Instant.now(), null, null));
                }
                return out;
            }
        };
    }

    private BookEnrichmentService serviceWith(BookMetadataSource... sources) {
        EventPublisher publisher = published::add;
        return new BookEnrichmentService(books, candidates, List.of(sources), publisher);
    }

    @Test
    void enrich_persistsEveryCandidateTheSourcesPropose() {
        Book b = books.save(book(orgId, "Refactoring", "Martin Fowler"));
        var service = serviceWith(source("OPEN_LIBRARY", 0.95, 0.4));

        var result = service.enrich(b.getId(), orgId);

        assertThat(result).hasSize(2);
        assertThat(candidates.byId).hasSize(2);
    }

    @Test
    void enrich_appliesAHighScoringMatchWithoutAHuman() {
        Book b = books.save(book(orgId, "Refactoring", "Martin Fowler"));
        var service = serviceWith(source("OPEN_LIBRARY", 0.95));

        service.enrich(b.getId(), orgId);

        Book after = books.findById(b.getId()).orElseThrow();
        assertThat(after.getIsbn13()).isEqualTo("9780000000001");
        assertThat(after.getPublisher()).isEqualTo("OUP");
        assertThat(after.getYear()).isEqualTo(2018);
        assertThat(after.getOpenLibraryKey()).isEqualTo("EXT-0.95");
        assertThat(after.getConfidence()).isEqualTo(Confidence.HIGH);
    }

    @Test
    void enrich_leavesTheBookUntouchedWhenTheBestMatchIsBelowThreshold() {
        Book b = books.save(book(orgId, "Refactoring", "Martin Fowler"));
        var service = serviceWith(source("OPEN_LIBRARY", 0.72));

        service.enrich(b.getId(), orgId);

        Book after = books.findById(b.getId()).orElseThrow();
        assertThat(after.getIsbn13()).isNull();
        assertThat(after.getOpenLibraryKey()).isNull();
    }

    @Test
    void enrich_queuesABelowThresholdMatchForReviewRatherThanDiscardingIt() {
        Book b = books.save(book(orgId, "Refactoring", "Martin Fowler"));
        var service = serviceWith(source("OPEN_LIBRARY", 0.72));

        service.enrich(b.getId(), orgId);

        assertThat(candidates.findPending(orgId, null, 10).items()).hasSize(1);
    }

    @Test
    void enrich_publishesBookEnrichedOnlyWhenAMatchIsApplied() {
        Book applied = books.save(book(orgId, "Refactoring", "Martin Fowler"));
        serviceWith(source("OPEN_LIBRARY", 0.95)).enrich(applied.getId(), orgId);
        assertThat(published).hasSize(1);
        assertThat(published.getFirst()).isInstanceOf(BookEnriched.class);

        published.clear();
        Book queued = books.save(book(orgId, "Small Worlds", "Duncan J. Watts"));
        serviceWith(source("OPEN_LIBRARY", 0.72)).enrich(queued.getId(), orgId);
        assertThat(published).isEmpty();
    }

    @Test
    void enrich_marksAnAutoAppliedCandidateAsDecidedSoItNeverReachesTheQueue() {
        Book b = books.save(book(orgId, "Refactoring", "Martin Fowler"));
        serviceWith(source("OPEN_LIBRARY", 0.95)).enrich(b.getId(), orgId);

        assertThat(candidates.findPending(orgId, null, 10).items()).isEmpty();
    }

    @Test
    void enrich_queriesEverySourceAndKeepsTheBestAcrossThem() {
        Book b = books.save(book(orgId, "Refactoring", "Martin Fowler"));
        var service = serviceWith(source("OPEN_LIBRARY", 0.55), source("GOOGLE_BOOKS", 0.93));

        var result = service.enrich(b.getId(), orgId);

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().score()).isEqualTo(0.93);
        assertThat(books.findById(b.getId()).orElseThrow().getIsbn13()).isNotNull();
    }

    @Test
    void enrich_survivesOneSourceFailingSoAnOutageDoesNotBlockTheOther() {
        Book b = books.save(book(orgId, "Refactoring", "Martin Fowler"));
        BookMetadataSource broken = new BookMetadataSource() {
            @Override
            public String sourceName() {
                return "BROKEN";
            }

            @Override
            public List<EnrichmentCandidate> findCandidates(Book book) {
                throw new IllegalStateException("circuit open");
            }
        };
        var service = serviceWith(broken, source("OPEN_LIBRARY", 0.95));

        var result = service.enrich(b.getId(), orgId);

        assertThat(result).hasSize(1);
        assertThat(books.findById(b.getId()).orElseThrow().getIsbn13()).isNotNull();
    }

    @Test
    void enrich_refusesToReadABookFromAnotherOrganization() {
        Book theirs = books.save(book(UuidFactory.newId(), "Refactoring", "Martin Fowler"));
        var service = serviceWith(source("OPEN_LIBRARY", 0.95));

        assertThatThrownBy(() -> service.enrich(theirs.getId(), orgId))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
