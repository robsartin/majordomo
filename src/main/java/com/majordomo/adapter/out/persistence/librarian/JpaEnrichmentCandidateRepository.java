package com.majordomo.adapter.out.persistence.librarian;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data repository for {@link EnrichmentCandidateEntity}.
 */
public interface JpaEnrichmentCandidateRepository
        extends JpaRepository<EnrichmentCandidateEntity, UUID> {

    /**
     * Lists every candidate proposed for one book, best-scoring first.
     *
     * @param bookId the book
     * @return the candidates
     */
    List<EnrichmentCandidateEntity> findByBookIdOrderByScoreDesc(UUID bookId);

    /**
     * Lists candidates awaiting a decision, scoped to an organization.
     *
     * <p>The candidates table carries no organization column of its own — org
     * ownership belongs to the book — so scoping joins through {@code books}
     * rather than denormalising a column the domain record could not populate.
     *
     * @param organizationId owning org
     * @param cursor         exclusive start cursor, or null for the first page
     * @param limit          how many rows to fetch
     * @return pending candidates in id order
     */
    @Query("""
            SELECT c FROM EnrichmentCandidateEntity c
            WHERE c.bookId IN (SELECT b.id FROM BookEntity b WHERE b.organizationId = :organizationId)
              AND c.reviewedAt IS NULL
              AND (:cursor IS NULL OR c.id > :cursor)
            ORDER BY c.id ASC
            """)
    List<EnrichmentCandidateEntity> findPending(@Param("organizationId") UUID organizationId,
                                                @Param("cursor") UUID cursor,
                                                Limit limit);
}
