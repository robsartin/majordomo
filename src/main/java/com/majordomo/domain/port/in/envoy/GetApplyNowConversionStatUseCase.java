package com.majordomo.domain.port.in.envoy;

import com.majordomo.domain.model.envoy.ApplyNowConversionStat;

import java.util.UUID;

/**
 * Inbound port: returns the "X of Y APPLY_NOW postings applied" rollup for an
 * organization. Used by the {@code /envoy} list page.
 */
public interface GetApplyNowConversionStatUseCase {

    /**
     * Returns the conversion stat for the most-recent APPLY_NOW reports in
     * {@code organizationId}. Implementations should bound the result set to a
     * sensible cap (matching the controller's recent-reports window) so the
     * computation stays cheap.
     *
     * @param organizationId owning org
     * @return the stat (always non-null; may be {@link ApplyNowConversionStat#EMPTY})
     */
    ApplyNowConversionStat getStat(UUID organizationId);
}
