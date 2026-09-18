package com.majordomo.domain.port.in.librarian;

import com.majordomo.domain.model.librarian.EnrichmentCandidate;

import java.util.List;
import java.util.UUID;

/**
 * Inbound port for matching a book against external catalogs.
 *
 * <p>Produces candidates rather than mutating the book. Whether a candidate is
 * applied automatically or queued for a human depends on its confidence — see
 * {@link ReviewEnrichmentUseCase}.
 */
public interface EnrichBookUseCase {

    /**
     * Queries the configured metadata sources for a book and persists what they
     * propose.
     *
     * @param bookId         the book to enrich
     * @param organizationId owning org
     * @return the candidates found, best-scoring first, empty when nothing matched
     */
    List<EnrichmentCandidate> enrich(UUID bookId, UUID organizationId);
}
