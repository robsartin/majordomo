package com.majordomo.adapter.out.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import com.majordomo.application.envoy.LlmScoringException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

import java.util.List;

/**
 * Thin wrapper over {@link AnthropicClient}. Applies a Resilience4j circuit breaker
 * and retry policy named {@code envoy-llm}. Callers supply a system prompt (marked
 * cacheable) and a user prompt; returns the assistant's first text block.
 */
public class AnthropicMessageClient {

    private final AnthropicClient client;
    private final String model;
    private final long maxTokens;

    /**
     * Constructs the wrapper.
     *
     * @param client    pre-built Anthropic SDK client
     * @param model     Anthropic model id (e.g. "claude-sonnet-4-6")
     * @param maxTokens response length cap
     */
    public AnthropicMessageClient(AnthropicClient client, String model, int maxTokens) {
        this.client = client;
        this.model = model;
        this.maxTokens = maxTokens;
    }

    /**
     * Returns the model id for reporting.
     *
     * @return the configured model identifier
     */
    public String model() {
        return model;
    }

    /**
     * Sends one Messages API call and returns the assistant's first text block.
     * The system prompt is attached with {@code cache_control: ephemeral} so
     * rubric-sized prompts stay in the 5-minute Anthropic prompt cache across
     * scoring calls.
     *
     * @param systemPrompt rubric-derived instructions (cached)
     * @param userPrompt   posting body
     * @return the assistant's first text block as a string
     */
    @CircuitBreaker(name = "envoy-llm")
    @Retry(name = "envoy-llm")
    public String send(String systemPrompt, String userPrompt) {
        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(maxTokens)
                    .systemOfTextBlockParams(List.of(
                            TextBlockParam.builder()
                                    .text(systemPrompt)
                                    .cacheControl(CacheControlEphemeral.builder().build())
                                    .build()))
                    .addUserMessage(userPrompt)
                    .build();
            Message message = client.messages().create(params);
            List<ContentBlock> blocks = message.content();
            if (blocks == null || blocks.isEmpty()) {
                throw new LlmScoringException("Anthropic API returned no content");
            }
            return blocks.get(0).text()
                    .map(tb -> tb.text())
                    .orElseThrow(() -> new LlmScoringException(
                            "First content block was not text"));
        } catch (LlmScoringException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmScoringException("Anthropic API call failed", e);
        }
    }
}
