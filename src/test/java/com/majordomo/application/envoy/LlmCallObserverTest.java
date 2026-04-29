package com.majordomo.application.envoy;

import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.Category;
import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.LlmScoreResponse;
import com.majordomo.domain.model.envoy.Rubric;
import com.majordomo.domain.model.envoy.Thresholds;
import com.majordomo.domain.model.envoy.Tier;
import com.majordomo.domain.port.out.envoy.LlmScoringPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link LlmCallObserver}. The observer's contract: invoke
 * {@code llm.score(...)} once, record a duration timer tagged with model +
 * outcome, and (when usage is present) increment input/output token counters
 * tagged with org + model + rubric.
 */
class LlmCallObserverTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final EnvoyMetrics metrics = new EnvoyMetrics(registry);
    private final LlmCallObserver observer = new LlmCallObserver(metrics);
    private final LlmScoringPort llm = mock(LlmScoringPort.class);

    private final UUID orgId = UuidFactory.newId();
    private JobPosting posting;
    private Rubric rubric;

    @BeforeEach
    void setUp() {
        posting = new JobPosting();
        posting.setId(UuidFactory.newId());
        posting.setOrganizationId(orgId);
        posting.setRawText("body");
        rubric = new Rubric(UuidFactory.newId(), Optional.empty(), 1, "default",
                List.of(),
                List.of(new Category("compensation", "pay", 20,
                        List.of(new Tier("Good", 15, "$200k")))),
                List.of(), new Thresholds(20, 15, 5), Instant.now());
        when(llm.modelId()).thenReturn("claude-sonnet-4-6");
    }

    /** Success path: returns the response, records success timer + token counters. */
    @Test
    void successPathRecordsTimerAndTokens() {
        when(llm.score(any(), any())).thenReturn(new LlmScoreResponse(
                Optional.empty(),
                List.of(new LlmScoreResponse.CategoryVerdict("compensation", "Good", "rationale")),
                List.of(),
                Optional.of(new LlmScoreResponse.Usage(100L, 50L, 200L))));

        var resp = observer.observe(llm, posting, rubric);

        assertThat(resp).isNotNull();
        assertThat(resp.usage()).isPresent();
        assertThat(registry.get("envoy_llm_call_duration")
                .tag("model", "claude-sonnet-4-6")
                .tag("outcome", "success")
                .timer().count()).isEqualTo(1L);
        assertThat(registry.get("envoy_llm_input_tokens_total")
                .tag("org", orgId.toString())
                .tag("model", "claude-sonnet-4-6")
                .tag("rubric", "default")
                .counter().count()).isEqualTo(100.0);
        assertThat(registry.get("envoy_llm_output_tokens_total")
                .tag("org", orgId.toString())
                .tag("model", "claude-sonnet-4-6")
                .tag("rubric", "default")
                .counter().count()).isEqualTo(50.0);
    }

    /** Success without usage: timer fires, token counters never registered. */
    @Test
    void successWithoutUsageRecordsTimerOnly() {
        when(llm.score(any(), any())).thenReturn(new LlmScoreResponse(
                Optional.empty(),
                List.of(new LlmScoreResponse.CategoryVerdict("compensation", "Good", "ok")),
                List.of(), Optional.empty()));

        observer.observe(llm, posting, rubric);

        assertThat(registry.get("envoy_llm_call_duration")
                .tag("outcome", "success").timer().count()).isEqualTo(1L);
        assertThat(registry.find("envoy_llm_input_tokens_total").counter()).isNull();
        assertThat(registry.find("envoy_llm_output_tokens_total").counter()).isNull();
    }

    /** Error path: re-throws the exception and records an error-outcome timer. */
    @Test
    void errorPathReThrowsAndRecordsErrorTimer() {
        when(llm.score(any(), any())).thenThrow(new LlmScoringException("boom"));

        assertThatThrownBy(() -> observer.observe(llm, posting, rubric))
                .isInstanceOf(LlmScoringException.class);

        assertThat(registry.get("envoy_llm_call_duration")
                .tag("model", "claude-sonnet-4-6")
                .tag("outcome", "error")
                .timer().count()).isEqualTo(1L);
        // No success-outcome sample.
        assertThat(registry.find("envoy_llm_call_duration")
                .tag("outcome", "success").timer()).isNull();
    }
}
