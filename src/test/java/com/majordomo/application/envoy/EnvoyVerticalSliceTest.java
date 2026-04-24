package com.majordomo.application.envoy;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.majordomo.adapter.out.llm.AnthropicMessageClient;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.JobSourceRequest;
import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.port.in.envoy.IngestJobPostingUseCase;
import com.majordomo.domain.port.in.envoy.ScoreJobPostingUseCase;
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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class EnvoyVerticalSliceTest {

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
