package com.majordomo.adapter.out.persistence.envoy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link JobPostingEntity}. */
public interface JpaJobPostingRepository extends JpaRepository<JobPostingEntity, UUID> {

    /**
     * Finds a posting by id within an organization.
     *
     * @param id             posting id
     * @param organizationId owning org
     * @return matching posting, or empty
     */
    Optional<JobPostingEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /**
     * Finds a posting by source + external id within an organization. Used for
     * dedup on re-ingestion.
     *
     * @param source         posting source discriminator
     * @param externalId     identifier from the upstream source
     * @param organizationId owning org
     * @return matching posting, or empty
     */
    Optional<JobPostingEntity> findBySourceAndExternalIdAndOrganizationId(
            String source, String externalId, UUID organizationId);

    /**
     * Returns all postings owned by the given organization. Used by the rescore
     * fan-out (manual endpoint and {@code RubricVersionCreated} listener) — at
     * personal scale (handful of postings) an unpaginated list is acceptable.
     *
     * @param organizationId owning org
     * @return every posting in that org (may be empty)
     */
    List<JobPostingEntity> findAllByOrganizationId(UUID organizationId);

    /**
     * Most-recent postings for an org, ordered by {@code fetched_at} descending.
     * Used as the default posting set for rubric A/B comparison.
     *
     * @param organizationId owning org
     * @param pageable       page metadata (caller passes a {@code PageRequest.of(0, limit)})
     * @return matching postings, newest first
     */
    List<JobPostingEntity> findByOrganizationIdOrderByFetchedAtDesc(
            UUID organizationId, org.springframework.data.domain.Pageable pageable);
}
