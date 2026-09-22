package com.majordomo.domain.port.out.envoy;

import com.majordomo.domain.model.envoy.ApplicationMaterial;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for generated application materials (#348, ADR-0028).
 *
 * <p>There is no update and no delete. Drafts are immutable and accumulate:
 * regenerating adds a record rather than replacing one, so what was actually
 * sent to a company stays answerable.
 */
public interface ApplicationMaterialRepository {

    /**
     * Persists a newly generated draft.
     *
     * @param material the draft
     * @return the saved draft
     */
    ApplicationMaterial save(ApplicationMaterial material);

    /**
     * Retrieves one draft within an organization.
     *
     * @param id             the draft id
     * @param organizationId the owning org
     * @return the draft, or empty if it does not exist or belongs elsewhere
     */
    Optional<ApplicationMaterial> findById(UUID id, UUID organizationId);

    /**
     * Returns every draft written for a posting, newest first.
     *
     * @param postingId      the posting
     * @param organizationId the owning org
     * @return the drafts, newest first; empty if there are none
     */
    List<ApplicationMaterial> findByPosting(UUID postingId, UUID organizationId);
}
