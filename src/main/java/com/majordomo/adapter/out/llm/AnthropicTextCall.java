package com.majordomo.adapter.out.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Usage;
import com.majordomo.application.envoy.LlmScoringException;
import com.majordomo.domain.model.envoy.LlmScoreResponse;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * One Anthropic Messages call, shared by the clients that wrap it.
 *
 * <p>Extracted so a second client can exist without a second copy of the SDK
 * plumbing. The wrappers differ only in which Resilience4j instance guards
 * them, and those names have to be annotation constants — which is the whole
 * reason there is more than one class.
 */
final class AnthropicTextCall {

    private AnthropicTextCall() { }

    /**
     * Sends one call and returns the first text block with usage metadata.
     *
     * <p>The system prompt is marked cacheable: it is identical across calls of
     * the same kind and large enough to be worth keeping in the prompt cache.
     *
     * @param client       the SDK client
     * @param model        model id
     * @param maxTokens    response length cap
     * @param systemPrompt the instructions
     * @param userPrompt   the request
     * @return the text and any usage the provider reported
     */
    static AnthropicMessageClient.MessageResult execute(
            AnthropicClient client, String model, long maxTokens,
            String systemPrompt, String userPrompt) {
        long startNs = System.nanoTime();
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
            String text = blocks.get(0).text()
                    .map(tb -> tb.text())
                    .orElseThrow(() -> new LlmScoringException(
                            "First content block was not text"));
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
            return new AnthropicMessageClient.MessageResult(text, usageOf(message, latencyMs));
        } catch (LlmScoringException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmScoringException("Anthropic API call failed", e);
        }
    }

    private static Optional<LlmScoreResponse.Usage> usageOf(Message message, long latencyMs) {
        try {
            Usage usage = message.usage();
            if (usage == null) {
                return Optional.empty();
            }
            return Optional.of(new LlmScoreResponse.Usage(
                    usage.inputTokens(), usage.outputTokens(), latencyMs));
        } catch (RuntimeException ignored) {
            // The SDK may surface usage as a lazily-parsed struct; losing the
            // token counts is not worth failing the call over.
            return Optional.empty();
        }
    }
}
