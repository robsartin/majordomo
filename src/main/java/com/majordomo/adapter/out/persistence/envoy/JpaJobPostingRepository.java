package com.majordomo.adapter.out.persistence.envoy;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
