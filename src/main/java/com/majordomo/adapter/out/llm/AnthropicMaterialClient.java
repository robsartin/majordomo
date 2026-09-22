package com.majordomo.adapter.out.llm;

import com.anthropic.client.AnthropicClient;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

/**
 * Anthropic client for drafting application materials (ADR-0028).
 *
 * <p>Exists as its own class purely for its own Resilience4j instance: the
 * names are annotation constants, so a separate breaker means a separate type.
 * Sharing {@code envoy-llm} would let a run of drafting failures open the
 * breaker scoring depends on, and the reverse — the same reason
 * {@link AnthropicVisionClient} is separate from {@link AnthropicMessageClient}.
 */
public class AnthropicMaterialClient {

    private final AnthropicClient client;
    private final String model;
    private final long maxTokens;

    /**
     * Constructs the client.
     *
     * @param client    the SDK client
     * @param model     model id
     * @param maxTokens response length cap
     */
    public AnthropicMaterialClient(AnthropicClient client, String model, int maxTokens) {
        this.client = client;
        this.model = model;
        this.maxTokens = maxTokens;
    }

    /**
     * Returns the model id, recorded on the draft for reproducibility.
     *
     * @return the configured model identifier
     */
    public String model() {
        return model;
    }

    /**
     * Sends one drafting call.
     *
     * @param systemPrompt the grounding rules
     * @param userPrompt   the posting, rationale and résumé
     * @return the draft text and any usage reported
     */
    @CircuitBreaker(name = "envoy-materials")
    @Retry(name = "envoy-materials")
    public AnthropicMessageClient.MessageResult sendWithUsage(
            String systemPrompt, String userPrompt) {
        return AnthropicTextCall.execute(client, model, maxTokens, systemPrompt, userPrompt);
    }
}
