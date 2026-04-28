package com.majordomo;

import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.JobSourceRequest;
import com.majordomo.domain.model.envoy.LlmScoreResponse;
import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.envoy.Rubric;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.port.in.envoy.IngestJobPostingUseCase;
import com.majordomo.domain.port.in.envoy.ScoreJobPostingUseCase;
import com.majordomo.domain.port.out.envoy.LlmScoringPort;
import com.majordomo.domain.port.out.envoy.RubricRepository;
import com.majordomo.domain.port.out.envoy.ScoreReportRepository;
import com.majordomo.domain.port.out.herald.NotificationPort;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end pipeline integration test for the Envoy slice.
 *
 * <p>Exercises the full ingest -&gt; score -&gt; persist -&gt; notify -&gt; metrics
 * path against a real PostgreSQL instance via Testcontainers (driven by the
 * {@code integration} profile on {@link IntegrationTest}). The two outbound
 * port boundaries that talk to the outside world are stubbed in-process:
 *
 * <ul>
 *     <li>{@link LlmScoringPort} returns a deterministic, high-scoring response
 *         with a populated {@link LlmScoreResponse.Usage} so we can exercise
 *         the token / latency persistence path added in #161.</li>
 *     <li>{@link NotificationPort} records sends in a list so we can assert
 *         the {@code APPLY_NOW} listener wired in #148 fired.</li>
 * </ul>
 *
 * <p><strong>Why {@code @SpringBootTest} (per ADR-0021)</strong>: this scenario
 * spans the application services, the JPA persistence adapter, the Spring event
 * bus that triggers the {@code HighScorePostingNotificationListener}, and the
 * Micrometer registry that the scorer increments. A slice would need to wire
 * all of those together by hand and would no longer be testing the
 * pipeline as it runs in production. The full context is the smallest
 * accurate harness for a true end-to-end assertion.
 */
@IntegrationTest
@Import(EnvoyEndToEndIntegrationTest.StubAdapters.class)
class EnvoyEndToEndIntegrationTest {

    /** The Personal organization seeded by V2__seed_default_user.sql. */
    private static final UUID SEED_ORG_ID =
            UUID.fromString("019606a0-0000-7000-8000-000000000003");

    @Autowired private IngestJobPostingUseCase ingest;
    @Autowired private ScoreJobPostingUseCase score;
    @Autowired private RubricRepository rubrics;
    @Autowired private ScoreReportRepository reports;
    @Autowired private MeterRegistry meterRegistry;
    @Autowired private RecordingNotificationPort notificationPort;
    @Autowired private StubLlmScoringPort llmScoringPort;

    /**
     * Drives the pipeline once, then asserts every observable outcome.
     */
    @Test
    void ingestScoringPersistAndNotificationPipeline() {
        // The system-default rubric (seeded in V15) is reachable for any org.
        Rubric activeRubric = rubrics.findActiveByName("default", SEED_ORG_ID).orElseThrow();
        // Stub the LLM to produce a score that crosses the APPLY_NOW threshold.
        llmScoringPort.responseSupplier = () -> applyNowResponse(activeRubric);

        // 1) Ingest a posting via the application port.
        JobPosting posting = ingest.ingest(
                new JobSourceRequest(
                        "manual",
                        "Senior Backend Engineer at Acme - Java/Spring/Postgres, "
                                + "fully remote US, Series C, base 260k.",
                        Map.of(
                                "company", "Acme",
                                "title", "Senior Backend Engineer",
                                "location", "Remote (US)")),
                SEED_ORG_ID);
        assertThat(posting.getId()).isNotNull();
        assertThat(posting.getOrganizationId()).isEqualTo(SEED_ORG_ID);

        // 2) Score against the seeded default rubric (LLM port stubbed).
        ScoreReport report = score.score(posting.getId(), "default", SEED_ORG_ID);

        // 3) Score crosses the APPLY_NOW threshold (75).
        assertThat(report.recommendation()).isEqualTo(Recommendation.APPLY_NOW);
        assertThat(report.finalScore()).isGreaterThanOrEqualTo(
                activeRubric.thresholds().applyImmediately());
        assertThat(report.usage()).isPresent();
        assertThat(report.usage().get().inputTokens()).isEqualTo(123L);
        assertThat(report.usage().get().outputTokens()).isEqualTo(45L);
        assertThat(report.usage().get().latencyMs()).isEqualTo(789L);

        // 4) The report is round-trippable from PostgreSQL with usage columns
        //    populated from the dedicated scalar columns added in #161
        //    (V16__envoy_score_report_usage.sql).
        ScoreReport reloaded = reports.findById(report.id(), SEED_ORG_ID).orElseThrow();
        assertThat(reloaded.recommendation()).isEqualTo(Recommendation.APPLY_NOW);
        assertThat(reloaded.usage()).contains(report.usage().get());

        // 5) Notification fired via the Spring event bus
        //    (HighScorePostingNotificationListener).
        assertThat(notificationPort.sent).hasSize(1);
        NotificationPayload sent = notificationPort.sent.get(0);
        assertThat(sent.to()).isEqualTo("rob.sartin@gmail.com");
        assertThat(sent.subject()).contains("Acme").contains("Senior Backend Engineer");
        assertThat(sent.body())
                .contains("APPLY_NOW")
                .contains(String.valueOf(report.finalScore()))
                .contains("/envoy/reports/" + report.id());

        // 6) Prometheus metrics from JobScorerService were incremented.
        String modelId = llmScoringPort.modelId();
        double inputTokens = meterRegistry
                .get("envoy_llm_input_tokens_total")
                .tag("org", SEED_ORG_ID.toString())
                .tag("model", modelId)
                .tag("rubric", "default")
                .counter().count();
        double outputTokens = meterRegistry
                .get("envoy_llm_output_tokens_total")
                .tag("org", SEED_ORG_ID.toString())
                .tag("model", modelId)
                .tag("rubric", "default")
                .counter().count();
        long timerCount = meterRegistry
                .get("envoy_llm_call_duration")
                .tag("model", modelId)
                .tag("outcome", "success")
                .timer().count();
        assertThat(inputTokens).isEqualTo(123.0);
        assertThat(outputTokens).isEqualTo(45.0);
        assertThat(timerCount).isEqualTo(1L);
        // The LLM port was hit exactly once across the whole pipeline.
        assertThat(llmScoringPort.calls.get()).isEqualTo(1);
    }

    /**
     * Builds an LLM response that selects the top tier for every category in
     * the rubric, hits no flags, and reports deterministic token usage. This
     * keeps the test resilient to rubric tweaks: any rubric the seed data
     * provides will score high.
     */
    private static LlmScoreResponse applyNowResponse(Rubric rubric) {
        List<LlmScoreResponse.CategoryVerdict> verdicts = new ArrayList<>();
        for (var category : rubric.categories()) {
            var topTier = category.tiers().stream()
                    .max((a, b) -> Integer.compare(a.points(), b.points()))
                    .orElseThrow();
            verdicts.add(new LlmScoreResponse.CategoryVerdict(
                    category.key(), topTier.label(),
                    "deterministic top-tier verdict for end-to-end test"));
        }
        return new LlmScoreResponse(
                Optional.empty(),
                verdicts,
                List.of(),
                Optional.of(new LlmScoreResponse.Usage(123L, 45L, 789L)));
    }

    /** Recorded notification payload, kept simple so assertions stay readable. */
    private record NotificationPayload(String to, String subject, String body) { }

    /** Test {@link NotificationPort} that records every send. */
    static final class RecordingNotificationPort implements NotificationPort {
        private final List<NotificationPayload> sent = new ArrayList<>();

        @Override
        public void send(String to, String subject, String body) {
            sent.add(new NotificationPayload(to, subject, body));
        }
    }

    /**
     * Test {@link LlmScoringPort} that delegates to a swap-able supplier so
     * each test method can shape the response. Counts invocations to make
     * accidental double-calls easy to spot in future tests.
     */
    static final class StubLlmScoringPort implements LlmScoringPort {
        Supplier<LlmScoreResponse> responseSupplier =
                () -> {
                    throw new IllegalStateException(
                            "StubLlmScoringPort.responseSupplier not configured");
                };
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public LlmScoreResponse score(JobPosting posting, Rubric rubric) {
            calls.incrementAndGet();
            return responseSupplier.get();
        }

        @Override
        public String modelId() {
            return "test-stub-model";
        }
    }

    /**
     * Replaces the production {@link LlmScoringPort} and {@link NotificationPort}
     * adapters with in-memory stubs. {@code @Primary} ensures these are picked
     * up even though the production beans are still on the classpath.
     */
    @TestConfiguration
    static class StubAdapters {
        @Bean
        @Primary
        RecordingNotificationPort recordingNotificationPort() {
            return new RecordingNotificationPort();
        }

        @Bean
        @Primary
        StubLlmScoringPort stubLlmScoringPort() {
            return new StubLlmScoringPort();
        }
    }
}
