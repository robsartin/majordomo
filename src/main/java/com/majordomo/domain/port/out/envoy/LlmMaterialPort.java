package com.majordomo.domain.port.out.envoy;

import com.majordomo.domain.model.envoy.LlmMaterialResponse;
import com.majordomo.domain.model.envoy.MaterialBrief;

/**
 * Outbound port for drafting application materials (ADR-0028).
 *
 * <p>Separate from {@code LlmScoringPort}, and on its own circuit breaker: a
 * run of drafting failures must not open the breaker scoring depends on, or the
 * reverse.
 */
public interface LlmMaterialPort {

    /**
     * Drafts one material.
     *
     * @param brief the posting, rationale, résumé, kind and tone
     * @return the draft and the claims it reports making
     */
    LlmMaterialResponse generate(MaterialBrief brief);

    /**
     * Model identifier recorded on the draft for reproducibility.
     *
     * @return the model id
     */
    String modelId();
}
