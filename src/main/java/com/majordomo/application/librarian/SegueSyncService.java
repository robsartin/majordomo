package com.majordomo.application.librarian;

import com.majordomo.domain.model.EntityNotFoundException;
import com.majordomo.domain.model.event.BookSyncedToInterestGraph;
import com.majordomo.domain.model.librarian.Book;
import com.majordomo.domain.port.in.librarian.SyncToInterestGraphUseCase;
import com.majordomo.domain.port.out.EventPublisher;
import com.majordomo.domain.port.out.librarian.BookRepository;
import com.majordomo.domain.port.out.librarian.InterestGraphLinkRepository;
import com.majordomo.domain.port.out.librarian.InterestGraphPort;
import com.majordomo.domain.port.out.librarian.WikidataLookupPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pushes a book's authors into the interest graph.
 *
 * <p>Every cataloged book syncs, carrying its rating rather than being filtered
 * on one (ADR-0023). The graph models its own taste scale and is better placed
 * to weight what it receives than this service is to withhold it — and Segue's
 * own documentation says low ratings are as useful as high ones.
 *
 * <p>What does stop a sync is a missing identifier. The graph joins on Wikidata
 * QIDs, so an author who cannot be resolved unambiguously is skipped rather than
 * pushed under a guess: a wrong QID attributes someone else's work in a graph
 * that outlives this catalog.
 */
@Service
public class SegueSyncService implements SyncToInterestGraphUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(SegueSyncService.class);

    private final BookRepository books;
    private final WikidataLookupPort wikidata;
    private final InterestGraphPort interestGraph;
    private final InterestGraphLinkRepository links;
    private final EventPublisher events;

    /**
     * Constructs the service with required outbound ports.
     *
     * @param books         the book repository
     * @param wikidata      the QID lookup
     * @param interestGraph the interest graph
     * @param links         the sync ledger
     * @param events        the domain event publisher
     */
    public SegueSyncService(BookRepository books,
                            WikidataLookupPort wikidata,
                            InterestGraphPort interestGraph,
                            InterestGraphLinkRepository links,
                            EventPublisher events) {
        this.books = books;
        this.wikidata = wikidata;
        this.interestGraph = interestGraph;
        this.links = links;
        this.events = events;
    }

    @Override
    @Transactional
    public boolean sync(UUID bookId, UUID organizationId) {
        Book book = books.findById(bookId)
                .filter(b -> organizationId.equals(b.getOrganizationId()))
                .orElseThrow(() -> new EntityNotFoundException("BOOK", bookId));

        List<String> authors = book.getAuthors();
        if (authors == null || authors.isEmpty()) {
            LOG.debug("Not syncing '{}': no authors recorded", book.getTitle());
            return false;
        }

        Map<String, String> authorQids = wikidata.findAuthorQids(authors);
        if (authorQids.isEmpty()) {
            LOG.info("Not syncing '{}': no author resolved to a Wikidata QID", book.getTitle());
            return false;
        }

        interestGraph.syncBook(book, authorQids);

        authorQids.values().forEach(qid -> links.record(bookId, qid));
        events.publish(new BookSyncedToInterestGraph(
                bookId, organizationId, book.getWikidataQid(),
                List.copyOf(authorQids.keySet()), book.getRating(), Instant.now()));
        return true;
    }
}
