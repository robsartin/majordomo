package com.majordomo.application.librarian;

import com.majordomo.domain.model.EntityNotFoundException;
import com.majordomo.domain.model.event.BookEnriched;
import com.majordomo.domain.model.librarian.Book;
import com.majordomo.domain.model.librarian.Confidence;
import com.majordomo.domain.model.librarian.EnrichmentCandidate;
import com.majordomo.domain.model.librarian.MatchScorer;
import com.majordomo.domain.port.in.librarian.EnrichBookUseCase;
import com.majordomo.domain.port.out.EventPublisher;
import com.majordomo.domain.port.out.librarian.BookMetadataSource;
import com.majordomo.domain.port.out.librarian.BookRepository;
import com.majordomo.domain.port.out.librarian.EnrichmentCandidateRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Matches books against external catalogs and decides what may be applied
 * without a human.
 *
 * <p>Only a match at or above {@link MatchScorer#AUTO_APPLY_THRESHOLD} is
 * written to the book. Everything weaker is persisted as a pending candidate
 * for the review queue instead — 24 of the 57 seed rows were read imperfectly
 * or not at all, and a catalog will return something plausible for every one of
 * them. Applying those unreviewed would put wrong identifiers into the catalog,
 * where they propagate into Wikidata lookups and then into the interest graph.
 */
@Service
public class BookEnrichmentService implements EnrichBookUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(BookEnrichmentService.class);

    private final BookRepository books;
    private final EnrichmentCandidateRepository candidates;
    private final List<BookMetadataSource> sources;
    private final EventPublisher events;

    /**
     * Constructs the service with required outbound ports.
     *
     * @param books      the book repository
     * @param candidates the enrichment candidate repository
     * @param sources    every configured metadata source
     * @param events     the domain event publisher
     */
    public BookEnrichmentService(BookRepository books,
                                 EnrichmentCandidateRepository candidates,
                                 List<BookMetadataSource> sources,
                                 EventPublisher events) {
        this.books = books;
        this.candidates = candidates;
        this.sources = sources;
        this.events = events;
    }

    @Override
    @Transactional
    public List<EnrichmentCandidate> enrich(UUID bookId, UUID organizationId) {
        Book book = books.findById(bookId)
                .filter(b -> organizationId.equals(b.getOrganizationId()))
                .orElseThrow(() -> new EntityNotFoundException("BOOK", bookId));

        List<EnrichmentCandidate> found = queryAllSources(book);
        if (found.isEmpty()) {
            return List.of();
        }

        List<EnrichmentCandidate> ranked = found.stream()
                .sorted(Comparator.comparingDouble(EnrichmentCandidate::score).reversed())
                .toList();

        EnrichmentCandidate best = ranked.getFirst();
        boolean autoApply = best.score() >= MatchScorer.AUTO_APPLY_THRESHOLD;

        var persisted = new ArrayList<EnrichmentCandidate>(ranked.size());
        for (EnrichmentCandidate candidate : ranked) {
            boolean isBest = candidate == best;
            persisted.add(candidates.save(
                    isBest && autoApply
                            ? candidate.withDecision(true, Instant.now())
                            : candidate));
        }

        if (autoApply) {
            apply(book, best);
            books.save(book);
            events.publish(new BookEnriched(
                    book.getId(), organizationId, best.source(), best.externalId(), Instant.now()));
        }
        return List.copyOf(persisted);
    }

    /**
     * Queries every source, tolerating one failing. A catalog being down must
     * not stop the others from answering — and must not look like "this book is
     * unknown", which is why the failure is logged rather than swallowed
     * silently.
     */
    private List<EnrichmentCandidate> queryAllSources(Book book) {
        var all = new ArrayList<EnrichmentCandidate>();
        for (BookMetadataSource source : sources) {
            try {
                all.addAll(source.findCandidates(book));
            } catch (RuntimeException e) {
                LOG.warn("Metadata source {} failed for book {}: {}",
                        source.sourceName(), book.getId(), e.toString());
            }
        }
        return all;
    }

    private void apply(Book book, EnrichmentCandidate candidate) {
        Map<String, String> payload = candidate.payload();
        if (payload.get("isbn13") != null) {
            book.setIsbn13(payload.get("isbn13"));
        }
        if (payload.get("publisher") != null) {
            book.setPublisher(payload.get("publisher"));
        }
        if (payload.get("year") != null) {
            try {
                book.setYear(Integer.valueOf(payload.get("year")));
            } catch (NumberFormatException e) {
                LOG.warn("Ignoring unparseable year '{}' from {}", payload.get("year"), candidate.source());
            }
        }
        book.setOpenLibraryKey(candidate.externalId());
        book.setConfidence(Confidence.HIGH);
    }
}
