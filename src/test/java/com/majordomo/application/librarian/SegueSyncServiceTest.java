package com.majordomo.application.librarian;

import com.majordomo.domain.model.EntityNotFoundException;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.event.BookSyncedToInterestGraph;
import com.majordomo.domain.model.librarian.Book;
import com.majordomo.domain.port.out.EventPublisher;
import com.majordomo.domain.port.out.librarian.InterestGraphLinkRepository;
import com.majordomo.domain.port.out.librarian.InterestGraphPort;
import com.majordomo.domain.port.out.librarian.WikidataLookupPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.majordomo.application.librarian.LibrarianEnrichmentTestSupport.FakeBooks;
import static com.majordomo.application.librarian.LibrarianEnrichmentTestSupport.book;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SegueSyncServiceTest {

    private static final class FakeLinks implements InterestGraphLinkRepository {
        private final Set<String> recorded = new LinkedHashSet<>();

        @Override
        public boolean record(UUID bookId, String qid) {
            return recorded.add(bookId + "|" + qid);
        }

        @Override
        public boolean exists(UUID bookId, String qid) {
            return recorded.contains(bookId + "|" + qid);
        }

        int size() {
            return recorded.size();
        }
    }

    private FakeBooks books;
    private FakeLinks links;
    private List<Object> published;
    private List<Map<String, String>> pushed;
    private Map<String, String> qidsToReturn;
    private SegueSyncService service;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        books = new FakeBooks();
        links = new FakeLinks();
        published = new ArrayList<>();
        pushed = new ArrayList<>();
        qidsToReturn = new LinkedHashMap<>();
        orgId = UuidFactory.newId();

        WikidataLookupPort wikidata = new WikidataLookupPort() {
            @Override
            public Optional<String> findQid(Book b) {
                return Optional.empty();
            }

            @Override
            public Map<String, String> findAuthorQids(List<String> names) {
                return qidsToReturn;
            }
        };
        InterestGraphPort graph = (b, authorQids) -> pushed.add(authorQids);
        EventPublisher publisher = published::add;
        service = new SegueSyncService(books, wikidata, graph, links, publisher);
    }

    private Book rated(String title, Integer rating, String... authors) {
        Book b = book(orgId, title, authors);
        b.setRating(rating);
        return books.save(b);
    }

    @Test
    void sync_pushesResolvedAuthorsToTheGraph() {
        qidsToReturn.put("Mark Newman", "Q1934063");
        Book b = rated("Networks", 4, "Mark Newman");

        assertThat(service.sync(b.getId(), orgId)).isTrue();
        assertThat(pushed).containsExactly(Map.of("Mark Newman", "Q1934063"));
    }

    @Test
    void sync_recordsALedgerRowPerAuthorQid() {
        qidsToReturn.put("Mark Needham", "Q1");
        qidsToReturn.put("Amy Hodler", "Q2");
        Book b = rated("Graph Algorithms", 5, "Mark Needham", "Amy Hodler");

        service.sync(b.getId(), orgId);

        assertThat(links.size()).isEqualTo(2);
    }

    @Test
    void sync_isIdempotentAndDoesNotDoubleWriteLedgerRows() {
        qidsToReturn.put("Mark Newman", "Q1934063");
        Book b = rated("Networks", 4, "Mark Newman");

        service.sync(b.getId(), orgId);
        service.sync(b.getId(), orgId);

        assertThat(links.size())
                .as("a re-sync must not duplicate the ledger")
                .isEqualTo(1);
    }

    @Test
    void sync_skipsABookWhoseAuthorCannotBeResolved() {
        Book b = rated("Obscure", 5, "Nobody Known");

        assertThat(service.sync(b.getId(), orgId))
                .as("never synced under a guessed identifier")
                .isFalse();
        assertThat(pushed).isEmpty();
        assertThat(published).isEmpty();
    }

    @Test
    void sync_skipsABookWithNoAuthorsRecorded() {
        Book b = rated("Anonymous", 5);

        assertThat(service.sync(b.getId(), orgId)).isFalse();
        assertThat(pushed).isEmpty();
    }

    @Test
    void sync_syncsAnUnratedBookToo() {
        qidsToReturn.put("Someone", "Q9");
        Book b = rated("Unrated", null, "Someone");

        assertThat(service.sync(b.getId(), orgId))
                .as("every cataloged book syncs; the rating rides along, it does not gate")
                .isTrue();
    }

    @Test
    void sync_publishesBookSyncedWithTheAuthorsAndRating() {
        qidsToReturn.put("Mark Newman", "Q1934063");
        Book b = rated("Networks", 4, "Mark Newman");

        service.sync(b.getId(), orgId);

        assertThat(published).hasSize(1);
        var event = (BookSyncedToInterestGraph) published.getFirst();
        assertThat(event.authors()).containsExactly("Mark Newman");
        assertThat(event.rating()).isEqualTo(4);
    }

    @Test
    void sync_refusesABookFromAnotherOrganization() {
        qidsToReturn.put("Mark Newman", "Q1934063");
        Book theirs = books.save(book(UuidFactory.newId(), "Networks", "Mark Newman"));

        assertThatThrownBy(() -> service.sync(theirs.getId(), orgId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void sync_letsAGraphFailureSurfaceRatherThanReportingSuccess() {
        qidsToReturn.put("Mark Newman", "Q1934063");
        Book b = rated("Networks", 4, "Mark Newman");
        var failing = new SegueSyncService(books,
                new WikidataLookupPort() {
                    @Override
                    public Optional<String> findQid(Book book) {
                        return Optional.empty();
                    }

                    @Override
                    public Map<String, String> findAuthorQids(List<String> names) {
                        return qidsToReturn;
                    }
                },
                (book, qids) -> {
                    throw new IllegalStateException("segue unreachable");
                },
                links, published::add);

        assertThatThrownBy(() -> failing.sync(b.getId(), orgId))
                .isInstanceOf(IllegalStateException.class);
        assertThat(links.size()).as("nothing recorded for a sync that did not happen").isZero();
    }
}
