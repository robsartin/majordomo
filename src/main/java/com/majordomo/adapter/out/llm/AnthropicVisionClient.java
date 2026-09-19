package com.majordomo.adapter.out.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

import java.util.List;

/**
 * Sends one image plus a prompt to the Messages API and returns the text answer.
 *
 * <p>Deliberately separate from {@link AnthropicMessageClient}: that one is
 * Envoy's, down to its {@code envoy-llm} circuit breaker. Sharing it would let
 * a run of shelf-photo failures trip the breaker that scoring depends on, and
 * the reverse.
 */
public class AnthropicVisionClient {

    private final AnthropicClient client;
    private final String model;
    private final int maxTokens;

    /**
     * Constructs the client.
     *
     * @param client    the SDK client
     * @param model     model id
     * @param maxTokens response length cap
     */
    public AnthropicVisionClient(AnthropicClient client, String model, int maxTokens) {
        this.client = client;
        this.model = model;
        this.maxTokens = maxTokens;
    }

    /**
     * Sends a system prompt, an image, and a user prompt.
     *
     * @param systemPrompt  instructions
     * @param userPrompt    the request about the image
     * @param base64Image   the image, base64-encoded without line breaks
     * @param mediaType     the image media type, e.g. {@code image/jpeg}
     * @return the assistant's first text block
     * @throws VisionCallException if the call fails or returns no text
     */
    @CircuitBreaker(name = "librarian-llm")
    @Retry(name = "librarian-llm")
    public String describe(String systemPrompt, String userPrompt,
                           String base64Image, String mediaType) {
        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(maxTokens)
                    .systemOfTextBlockParams(List.of(
                            TextBlockParam.builder().text(systemPrompt).build()))
                    .addUserMessageOfBlockParams(List.of(
                            ContentBlockParam.ofImage(ImageBlockParam.builder()
                                    .source(Base64ImageSource.builder()
                                            .mediaType(Base64ImageSource.MediaType.of(mediaType))
                                            .data(base64Image)
                                            .build())
                                    .build()),
                            ContentBlockParam.ofText(
                                    TextBlockParam.builder().text(userPrompt).build())))
                    .build();

            Message message = client.messages().create(params);
            List<ContentBlock> blocks = message.content();
            if (blocks == null || blocks.isEmpty()) {
                throw new VisionCallException("Anthropic API returned no content", null);
            }
            return blocks.get(0).text()
                    .map(tb -> tb.text())
                    .orElseThrow(() -> new VisionCallException(
                            "First content block was not text", null));
        } catch (VisionCallException e) {
            throw e;
        } catch (Exception e) {
            throw new VisionCallException("Anthropic vision call failed", e);
        }
    }
}
