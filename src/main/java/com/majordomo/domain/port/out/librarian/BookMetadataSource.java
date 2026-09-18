package com.majordomo.domain.port.out.librarian;

import com.majordomo.domain.model.librarian.Book;
import com.majordomo.domain.model.librarian.EnrichmentCandidate;

import java.util.List;

/**
 * Outbound port for an external book catalog such as Open Library.
 *
 * <p>Implementations are expected to be guarded by a circuit breaker and retry,
 * as the other outbound HTTP adapters are, and to return an empty list rather
 * than throwing when a book simply is not found. Coverage is uneven for older
 * technical books, so not matching is an ordinary outcome, not an error.
 */
public interface BookMetadataSource {

    /**
     * Names this source, for recording on the candidates it produces.
     *
     * @return a stable source identifier, e.g. {@code OPEN_LIBRARY}
     */
    String sourceName();

    /**
     * Searches for candidate matches for a book.
     *
     * @param book the book to match
     * @return candidate matches, best-scoring first, empty when nothing matched
     */
    List<EnrichmentCandidate> findCandidates(Book book);
}
