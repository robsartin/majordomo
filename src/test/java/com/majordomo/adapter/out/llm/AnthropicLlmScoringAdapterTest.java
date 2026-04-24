package com.majordomo.adapter.out.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.majordomo.application.envoy.LlmScoringException;
import com.majordomo.application.envoy.PromptBuilder;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.Category;
import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.LlmScoreResponse;
import com.majordomo.domain.model.envoy.Rubric;
import com.majordomo.domain.model.envoy.Thresholds;
import com.majordomo.domain.model.envoy.Tier;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnthropicLlmScoringAdapterTest {

    private MockWebServer server;
    private AnthropicLlmScoringAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey("test-key")
                .baseUrl(server.url("/").toString())
                .maxRetries(0)
                .build();
        adapter = new AnthropicLlmScoringAdapter(
                new AnthropicMessageClient(client, "claude-sonnet-4-6", 4096),
                new PromptBuilder());
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private Rubric rubric() {
        return new Rubric(UuidFactory.newId(), Optional.empty(), 1, "default",
                List.of(),
                List.of(new Category("compensation", "pay", 20,
                        List.of(new Tier("Good", 15, "$200-250k")))),
                List.of(), new Thresholds(20, 15, 5), Instant.now());
    }

    private JobPosting posting() {
        var p = new JobPosting();
        p.setOrganizationId(UuidFactory.newId());
        p.setRawText("We pay well");
        return p;
    }

    @Test
    void parsesValidAnthropicResponse() {
        String innerJson = "{\\\"disqualifierKey\\\":null,"
                + "\\\"categoryVerdicts\\\":[{\\\"categoryKey\\\":\\\"compensation\\\","
                + "\\\"tierLabel\\\":\\\"Good\\\",\\\"rationale\\\":\\\"listed salary\\\"}],"
                + "\\\"flagHits\\\":[]}";
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "id": "msg_01",
                          "type": "message",
                          "role": "assistant",
                          "model": "claude-sonnet-4-6",
                          "stop_reason": "end_turn",
                          "usage": {"input_tokens": 10, "output_tokens": 10},
                          "content": [
                            {"type": "text", "text": "%s"}
                          ]
                        }
                        """.formatted(innerJson)));

        LlmScoreResponse resp = adapter.score(posting(), rubric());

        assertThat(resp.disqualifierKey()).isEmpty();
        assertThat(resp.categoryVerdicts()).hasSize(1);
        assertThat(resp.categoryVerdicts().get(0).tierLabel()).isEqualTo("Good");
    }

    @Test
    void throwsOnNon2xx() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("{}"));
        assertThatThrownBy(() -> adapter.score(posting(), rubric()))
                .isInstanceOf(LlmScoringException.class);
    }

    @Test
    void throwsOnMalformedJsonInAssistantMessage() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "id": "msg_01", "type": "message", "role": "assistant",
                          "model": "claude-sonnet-4-6", "stop_reason": "end_turn",
                          "usage": {"input_tokens": 1, "output_tokens": 1},
                          "content": [{"type": "text", "text": "not json at all"}]
                        }
                        """));
        assertThatThrownBy(() -> adapter.score(posting(), rubric()))
                .isInstanceOf(LlmScoringException.class);
    }
}
