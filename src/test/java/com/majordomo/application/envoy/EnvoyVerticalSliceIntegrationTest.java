package com.majordomo.application.envoy;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.majordomo.adapter.out.llm.AnthropicMessageClient;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.Category;
import com.majordomo.domain.model.envoy.Flag;
import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.JobSourceRequest;
import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.envoy.Rubric;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.model.envoy.Thresholds;
import com.majordomo.domain.model.envoy.Tier;
import com.majordomo.domain.port.in.envoy.IngestJobPostingUseCase;
import com.majordomo.domain.port.in.envoy.ScoreJobPostingUseCase;
import com.majordomo.domain.port.out.envoy.RubricRepository;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vertical slice covering ingest → score → persist → notify with a mocked
 * Anthropic client. Renamed to {@code *IntegrationTest} so it runs only under
 * the {@code integration-tests} profile (Testcontainers Postgres) — the
 * default H2 test profile rejects PostgreSQL-flavored array DDL like
 * {@code text[]} that Hibernate generates for {@link com.majordomo.adapter.out.persistence.concierge.ContactEntity}
 * and {@code user_preferences}, causing ordering-dependent failures when the
 * full unit-test suite runs (issue #257).
 */
@SpringBootTest
@ActiveProfiles("integration")
class EnvoyVerticalSliceIntegrationTest {

    private static MockWebServer server;
    private static final UUID ORG_ID = UuidFactory.newId();

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        AnthropicMessageClient testMessageClient() throws Exception {
            server = new MockWebServer();
            server.start();
            AnthropicClient client = AnthropicOkHttpClient.builder()
                    .apiKey("test-key")
                    .baseUrl(server.url("/").toString())
                    .maxRetries(0)
                    .build();
            return new AnthropicMessageClient(client, "claude-sonnet-4-6", 4096);
        }
    }

    @Autowired IngestJobPostingUseCase ingest;
    @Autowired ScoreJobPostingUseCase score;
    @Autowired RubricRepository rubrics;

    @BeforeEach
    void seedDefaultRubric() {
        rubrics.save(new Rubric(
                UuidFactory.newId(), Optional.empty(), 1, "default",
                List.of(),
                List.of(
                        cat("compensation", 25, tier("Good", 18)),
                        cat("remote", 15, tier("Full remote", 15)),
                        cat("role_scope", 20, tier("Aligned", 12)),
                        cat("team_signals", 15, tier("Generic", 6)),
                        cat("company_stage", 15, tier("Growth", 9)),
                        cat("tech_stack", 10, tier("Perfect", 10))),
                List.of(new Flag("AT_WILL_EMPHASIS", "agg at-will", 3)),
                new Thresholds(75, 55, 35),
                Instant.now()));
    }

    private static Category cat(String key, int max, Tier t) {
        return new Category(key, key, max, List.of(t));
    }

    private static Tier tier(String label, int points) {
        return new Tier(label, points, label);
    }

    @BeforeEach
    void enqueueAnthropicResponse() {
        String innerJson = "{\\\"disqualifierKey\\\":null,\\\"categoryVerdicts\\\":["
                + "{\\\"categoryKey\\\":\\\"compensation\\\",\\\"tierLabel\\\":\\\"Good\\\","
                + "\\\"rationale\\\":\\\"$225k base listed\\\"},"
                + "{\\\"categoryKey\\\":\\\"remote\\\",\\\"tierLabel\\\":\\\"Full remote\\\","
                + "\\\"rationale\\\":\\\"fully remote US\\\"},"
                + "{\\\"categoryKey\\\":\\\"role_scope\\\",\\\"tierLabel\\\":\\\"Aligned\\\","
                + "\\\"rationale\\\":\\\"Senior backend\\\"},"
                + "{\\\"categoryKey\\\":\\\"team_signals\\\",\\\"tierLabel\\\":\\\"Generic\\\","
                + "\\\"rationale\\\":\\\"typical language\\\"},"
                + "{\\\"categoryKey\\\":\\\"company_stage\\\",\\\"tierLabel\\\":\\\"Growth\\\","
                + "\\\"rationale\\\":\\\"Series C\\\"},"
                + "{\\\"categoryKey\\\":\\\"tech_stack\\\",\\\"tierLabel\\\":\\\"Perfect\\\","
                + "\\\"rationale\\\":\\\"Java/Spring/Postgres\\\"}],"
                + "\\\"flagHits\\\":[]}";
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "id": "msg_01", "type": "message", "role": "assistant",
                          "model": "claude-sonnet-4-6", "stop_reason": "end_turn",
                          "usage": {"input_tokens": 10, "output_tokens": 10},
                          "content": [
                            {"type": "text", "text": "%s"}
                          ]
                        }
                        """.formatted(innerJson)));
    }

    @AfterEach
    void shutdown() throws Exception {
        server.shutdown();
    }

    @Test
    void manualPaste_scores_producesReport() {
        String body = "Senior Backend Engineer at Acme — Java/Spring/Postgres, "
                + "fully remote US, Series C, $225k base.";
        JobPosting posting = ingest.ingest(new JobSourceRequest(
                        "manual",
                        body,
                        Map.of("company", "Acme", "title", "Senior Engineer")),
                ORG_ID);

        ScoreReport report = score.score(posting.getId(), "default", ORG_ID);

        assertThat(report.recommendation()).isIn(
                Recommendation.APPLY_NOW, Recommendation.APPLY, Recommendation.CONSIDER);
        assertThat(report.finalScore()).isGreaterThan(0);
        assertThat(report.categoryScores()).hasSize(6);
        assertThat(report.organizationId()).isEqualTo(ORG_ID);
    }
}
