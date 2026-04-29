package com.majordomo.application.envoy;

import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.LlmScoreResponse;
import com.majordomo.domain.model.envoy.Rubric;
import com.majordomo.domain.port.out.envoy.LlmScoringPort;
import org.springframework.stereotype.Component;

/**
 * Wraps a single {@link LlmScoringPort#score} call with observability:
 * latency timer (success / error outcome) and token-usage counters when the
 * provider supplies a usage payload. Lets {@link JobScorerService} stay pure
 * orchestration — no timer plumbing, no try/catch around the LLM call.
 */
@Component
public class LlmCallObserver {

    private final EnvoyMetrics metrics;

    /**
     * Constructs the observer.
     *
     * @param metrics envoy metrics helper
     */
    public LlmCallObserver(EnvoyMetrics metrics) {
        this.metrics = metrics;
    }

    /**
     * Invokes {@code llm.score(posting, rubric)} with observation around it.
     * Records the call duration tagged with model + outcome, and increments
     * token counters tagged with org + model + rubric when usage data is
     * present. Re-throws any {@link RuntimeException} from the underlying
     * call after recording an {@code error}-outcome timer sample.
     *
     * @param llm     LLM scoring port
     * @param posting posting being scored (provides org id for token tags)
     * @param rubric  rubric used (provides rubric name for token tags)
     * @return the LLM's structured verdict
     */
    public LlmScoreResponse observe(LlmScoringPort llm, JobPosting posting, Rubric rubric) {
        String modelId = llm.modelId();
        long startNs = System.nanoTime();
        try {
            LlmScoreResponse resp = llm.score(posting, rubric);
            metrics.recordLlmCallDuration(modelId, "success", System.nanoTime() - startNs);
            resp.usage().ifPresent(usage -> metrics.recordLlmTokenUsage(
                    posting.getOrganizationId(), modelId, rubric.name(),
                    usage.inputTokens(), usage.outputTokens()));
            return resp;
        } catch (RuntimeException e) {
            metrics.recordLlmCallDuration(modelId, "error", System.nanoTime() - startNs);
            throw e;
        }
    }
}
