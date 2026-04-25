package com.majordomo.domain.port.in.envoy;

import com.majordomo.domain.model.envoy.Rubric;

import java.util.UUID;

/** Inbound port for rubric authoring. */
public interface ManageRubricUseCase {

    /**
     * Appends a new org-specific version of the named rubric. The caller's
     * {@code id}, {@code organizationId}, {@code version}, and {@code effectiveFrom}
     * on {@code rubric} are ignored — the service assigns them.
     *
     * @param name           the rubric name whose next version is being created
     * @param rubric         body for the new version (categories, flags, disqualifiers, thresholds)
     * @param organizationId the owning org
     * @return the saved rubric with its new id, organizationId, version, and effectiveFrom
     */
    Rubric saveNewVersion(String name, Rubric rubric, UUID organizationId);
}
