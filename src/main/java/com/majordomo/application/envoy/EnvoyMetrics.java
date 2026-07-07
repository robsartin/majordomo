package com.majordomo.application.envoy;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Owns the metric names, descriptions, and tag conventions for the Envoy
 * service so {@link JobScorerService} and {@link PostingConversionService}
 * don't each carry their own {@code Counter.builder(...)} chains. Metric names
 * and tag keys are intentionally part of this class's public API surface — they
 * are also part of the dashboard contract.
 */
@Component
public class EnvoyMetrics {

    /** Counter: prompt tokens consumed by Envoy LLM scoring calls. */
    static final String LLM_INPUT_TOKENS = "envoy_llm_input_tokens_total";

    /** Counter: completion tokens produced by Envoy LLM scoring calls. */
    static final String LLM_OUTPUT_TOKENS = "envoy_llm_output_tokens_total";

    /** Timer: wall-clock duration of Envoy LLM scoring calls. */
    static final String LLM_CALL_DURATION = "envoy_llm_call_duration";

    /** Counter: APPLY_NOW conversions, by outcome (applied / dismissed). */
    static final String APPLY_NOW_CONVERSION = "envoy_apply_now_conversion_total";

    /** Counter: idempotent-scoring cache lookups, by outcome (hit / miss). */
    static final String SCORE_CACHE = "envoy_score_cache_total";

    static final String TAG_ORG = "org";
    static final String TAG_MODEL = "model";
    static final String TAG_RUBRIC = "rubric";
    static final String TAG_OUTCOME = "outcome";

    /** Outcome tag values for {@link #APPLY_NOW_CONVERSION}. */
    public enum ConversionOutcome {
        APPLIED("applied"),
        DISMISSED("dismissed");

        private final String tag;

        ConversionOutcome(String tag) {
            this.tag = tag;
        }

        String tag() {
            return tag;
        }
    }

    /** Outcome tag values for {@link #SCORE_CACHE}. */
    public enum ScoreCacheOutcome {
        /** A prior report for an unchanged posting was reused; no LLM call. */
        HIT("hit"),
        /** No reusable report existed; the LLM was invoked. */
        MISS("miss");

        private final String tag;

        ScoreCacheOutcome(String tag) {
            this.tag = tag;
        }

        String tag() {
            return tag;
        }
    }

    private final MeterRegistry meterRegistry;

    /**
     * Constructs the helper.
     *
     * @param meterRegistry Micrometer registry to record into
     */
    public EnvoyMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Records the wall-clock duration of an LLM scoring call.
     *
     * @param modelId   model identifier (e.g. {@code claude-sonnet-4-6})
     * @param outcome   {@code success} or {@code error}
     * @param elapsedNs elapsed time in nanoseconds
     */
    public void recordLlmCallDuration(String modelId, String outcome, long elapsedNs) {
        Timer.builder(LLM_CALL_DURATION)
                .description("Wall-clock duration of envoy LLM scoring calls")
                .tag(TAG_MODEL, modelId)
                .tag(TAG_OUTCOME, outcome)
                .register(meterRegistry)
                .record(elapsedNs, TimeUnit.NANOSECONDS);
    }

    /**
     * Increments the LLM token counters by the per-call usage.
     *
     * @param organizationId owning org (used as a tag value)
     * @param modelId        model identifier
     * @param rubricName     rubric name used for the call
     * @param inputTokens    prompt tokens consumed
     * @param outputTokens   completion tokens produced
     */
    public void recordLlmTokenUsage(UUID organizationId, String modelId, String rubricName,
                                    long inputTokens, long outputTokens) {
        Counter.builder(LLM_INPUT_TOKENS)
                .description("Prompt tokens consumed by envoy LLM scoring calls")
                .tag(TAG_ORG, organizationId.toString())
                .tag(TAG_MODEL, modelId)
                .tag(TAG_RUBRIC, rubricName)
                .register(meterRegistry)
                .increment(inputTokens);
        Counter.builder(LLM_OUTPUT_TOKENS)
                .description("Completion tokens produced by envoy LLM scoring calls")
                .tag(TAG_ORG, organizationId.toString())
                .tag(TAG_MODEL, modelId)
                .tag(TAG_RUBRIC, rubricName)
                .register(meterRegistry)
                .increment(outputTokens);
    }

    /**
     * Increments the APPLY_NOW conversion counter for the given outcome.
     *
     * @param organizationId owning org (used as a tag value)
     * @param outcome        {@link ConversionOutcome#APPLIED} or {@link ConversionOutcome#DISMISSED}
     */
    public void recordApplyNowConversion(UUID organizationId, ConversionOutcome outcome) {
        Counter.builder(APPLY_NOW_CONVERSION)
                .description("APPLY_NOW posting conversions, by outcome")
                .tag(TAG_ORG, organizationId.toString())
                .tag(TAG_OUTCOME, outcome.tag())
                .register(meterRegistry)
                .increment();
    }

    /**
     * Records the outcome of an idempotent-scoring cache lookup. A {@code HIT}
     * means a prior report was reused and no LLM call was made; a {@code MISS}
     * means the LLM was invoked. The hit rate over these two is the token-cost
     * saving from idempotent scoring. Forced rescores never consult the cache
     * and so are not recorded here.
     *
     * @param rubricName the rubric name the lookup was scoped to
     * @param outcome    {@link ScoreCacheOutcome#HIT} or {@link ScoreCacheOutcome#MISS}
     */
    public void recordScoreCacheOutcome(String rubricName, ScoreCacheOutcome outcome) {
        Counter.builder(SCORE_CACHE)
                .description("Idempotent-scoring cache lookups, by outcome")
                .tag(TAG_RUBRIC, rubricName)
                .tag(TAG_OUTCOME, outcome.tag())
                .register(meterRegistry)
                .increment();
    }
}
