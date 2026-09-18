package com.majordomo.domain.port.in.librarian;

import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.librarian.EnrichmentCandidate;

import java.util.UUID;

/**
 * Inbound port for the review queue that stands between a lossy transcription
 * and the catalog.
 *
 * <p>Roughly half the seed shelf carried a transcription caveat, and those rows
 * are exactly the ones an external catalog matches confidently and wrongly. A
 * wrong identifier accepted here propagates into Wikidata lookups and then into
 * the interest graph, where it is far harder to spot.
 */
public interface ReviewEnrichmentUseCase {

    /**
     * Lists candidates awaiting a human decision.
     *
     * @param organizationId owning org
     * @param cursor         the id to resume after, or null to start
     * @param limit          page size
     * @return a page of pending candidates
     */
    Page<EnrichmentCandidate> pending(UUID organizationId, UUID cursor, int limit);

    /**
     * Applies a candidate's payload to its book.
     *
     * @param candidateId    the candidate to accept
     * @param organizationId owning org
     */
    void accept(UUID candidateId, UUID organizationId);

    /**
     * Rejects a candidate, leaving the book unchanged. The candidate is retained
     * so the decision leaves a trail.
     *
     * @param candidateId    the candidate to reject
     * @param organizationId owning org
     */
    void reject(UUID candidateId, UUID organizationId);
}
