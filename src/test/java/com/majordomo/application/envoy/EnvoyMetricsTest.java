package com.majordomo.application.envoy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link EnvoyMetrics}.
 *
 * <p>The metric names and tag keys exercised here are part of the dashboard
 * contract; these assertions exist explicitly to break loudly if a refactor
 * changes either.</p>
 */
class EnvoyMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final EnvoyMetrics metrics = new EnvoyMetrics(registry);

    /** Token-usage call increments the input + output counters with the expected tag keys. */
    @Test
    void recordLlmTokenUsageIncrementsBothCounters() {
        UUID orgId = UUID.randomUUID();

        metrics.recordLlmTokenUsage(orgId, "claude-sonnet-4-6", "default", 100, 50);

        var input = registry.get("envoy_llm_input_tokens_total")
                .tag("org", orgId.toString())
                .tag("model", "claude-sonnet-4-6")
                .tag("rubric", "default")
                .counter();
        var output = registry.get("envoy_llm_output_tokens_total")
                .tag("org", orgId.toString())
                .tag("model", "claude-sonnet-4-6")
                .tag("rubric", "default")
                .counter();
        assertThat(input.count()).isEqualTo(100.0);
        assertThat(output.count()).isEqualTo(50.0);
    }

    /** LLM call duration timer is recorded under the model + outcome tag pair. */
    @Test
    void recordLlmCallDurationRecordsTimer() {
        metrics.recordLlmCallDuration("claude-sonnet-4-6", "success", 12_345_678L);

        var timer = registry.get("envoy_llm_call_duration")
                .tag("model", "claude-sonnet-4-6")
                .tag("outcome", "success")
                .timer();
        assertThat(timer.count()).isEqualTo(1L);
    }

    /** APPLY_NOW conversion counter is tagged by org and outcome={applied,dismissed}. */
    @Test
    void recordApplyNowConversionUsesCorrectTags() {
        UUID orgId = UUID.randomUUID();

        metrics.recordApplyNowConversion(orgId, EnvoyMetrics.ConversionOutcome.APPLIED);
        metrics.recordApplyNowConversion(orgId, EnvoyMetrics.ConversionOutcome.DISMISSED);

        var applied = registry.get("envoy_apply_now_conversion_total")
                .tag("org", orgId.toString())
                .tag("outcome", "applied")
                .counter();
        var dismissed = registry.get("envoy_apply_now_conversion_total")
                .tag("org", orgId.toString())
                .tag("outcome", "dismissed")
                .counter();
        assertThat(applied.count()).isEqualTo(1.0);
        assertThat(dismissed.count()).isEqualTo(1.0);
    }
}
