package com.majordomo.domain.port.in.envoy;

import com.majordomo.domain.model.envoy.ApplyNowPosting;

import java.util.List;
import java.util.UUID;

/**
 * Inbound port: list the most recent APPLY_NOW score reports for an organization,
 * enriched with posting metadata for display on summary surfaces (e.g. the dashboard).
 */
public interface GetRecentApplyNowPostingsUseCase {

    /**
     * Returns the most recent APPLY_NOW reports in the org, newest first, capped at
     * {@code limit}. Implementations should clamp {@code limit} to a sensible range.
     *
     * @param organizationId owning org
     * @param limit          maximum rows to return
     * @return list of enriched APPLY_NOW rows (may be empty)
     */
    List<ApplyNowPosting> getRecentApplyNow(UUID organizationId, int limit);
}
