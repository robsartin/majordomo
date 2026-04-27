package com.majordomo.domain.port.out.envoy;

import com.majordomo.domain.model.envoy.JobPosting;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for job-posting persistence.
 */
public interface JobPostingRepository {

    /**
     * Persists a new posting or updates an existing one by id.
     *
     * @param posting the posting to persist
     * @return the persisted posting
     */
    JobPosting save(JobPosting posting);

    /**
     * Finds a posting by id, scoped to an organization.
     *
     * @param id             the posting id
     * @param organizationId org scope
     * @return the posting, or empty if not found within that org
     */
    Optional<JobPosting> findById(UUID id, UUID organizationId);

    /**
     * Finds a posting by source + external id within an organization. Used to
     * deduplicate re-ingestion from the same upstream job board per org.
     *
     * @param source         posting source discriminator (e.g. "manual", "greenhouse")
     * @param externalId     identifier from the upstream source
     * @param organizationId org scope
     * @return an existing posting with that source+externalId, or empty
     */
    Optional<JobPosting> findBySourceAndExternalId(String source, String externalId, UUID organizationId);

    /**
     * Returns every posting owned by an organization. Used by the rescore
     * fan-out (manual endpoint and {@code RubricVersionCreated} listener).
     * Unpaginated by design — see issue #147 for the personal-scale rationale.
     *
     * @param organizationId owning org
     * @return every posting in that org (may be empty)
     */
    List<JobPosting> findAllByOrganizationId(UUID organizationId);
}
