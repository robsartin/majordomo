package com.majordomo.domain.port.out.envoy;

import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.LlmScoreResponse;
import com.majordomo.domain.model.envoy.Rubric;

/**
 * Outbound port for LLM-driven posting scoring. Implementations send the rubric +
 * posting to an LLM and return a parsed, structured response. Implementations must
 * validate the response is well-formed JSON matching {@link LlmScoreResponse} —
 * deep rubric validation (tier labels, disqualifier keys) is performed upstream
 * by {@code ScoreAssembler}.
 */
public interface LlmScoringPort {

    /**
     * Scores a posting against a rubric.
     *
     * @param posting the posting to score
     * @param rubric  the active rubric
     * @return the LLM's structured verdict
     * @throws com.majordomo.application.envoy.LlmScoringException if the LLM call
     *         fails or returns unparseable output
     */
    LlmScoreResponse score(JobPosting posting, Rubric rubric);

    /**
     * Model identifier recorded on score reports for reproducibility.
     *
     * @return the LLM model identifier (e.g. "claude-sonnet-4-6")
     */
    String modelId();
}
