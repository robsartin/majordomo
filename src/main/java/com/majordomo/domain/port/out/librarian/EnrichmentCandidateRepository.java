package com.majordomo.domain.port.out.librarian;

import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.librarian.EnrichmentCandidate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for persisting proposed external matches.
 *
 * <p>Candidates outlive the decision made on them: a rejected match is retained
 * rather than deleted, so a later reviewer can see what was already turned down.
 */
public interface EnrichmentCandidateRepository {

    /**
     * Persists a candidate.
     *
     * @param candidate the candidate to save
     * @return the saved candidate
     */
    EnrichmentCandidate save(EnrichmentCandidate candidate);

    /**
     * Retrieves a candidate by its unique identifier.
     *
     * @param id the candidate ID
     * @return the candidate, or empty if not found
     */
    Optional<EnrichmentCandidate> findById(UUID id);

    /**
     * Lists every candidate proposed for one book, best-scoring first.
     *
     * @param bookId the book
     * @return the candidates, empty when none were proposed
     */
    List<EnrichmentCandidate> findByBookId(UUID bookId);

    /**
     * Lists candidates awaiting a human decision.
     *
     * @param organizationId owning org
     * @param cursor         the id to resume after, or null to start
     * @param limit          page size
     * @return a page of pending candidates
     */
    Page<EnrichmentCandidate> findPending(UUID organizationId, UUID cursor, int limit);
}
