package com.majordomo.adapter.out.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the Anthropic SDK transport. Reads API key, model,
 * and token limit from {@code envoy.llm.*} properties.
 */
@Configuration
public class LlmConfiguration {

    /**
     * Constructs the shared Anthropic SDK client.
     *
     * @param apiKey  Anthropic API key ({@code envoy.llm.api-key})
     * @param baseUrl optional API base URL override ({@code envoy.llm.base-url})
     * @return a configured {@link AnthropicClient}
     */
    @Bean
    public AnthropicClient anthropicClient(
            @Value("${envoy.llm.api-key}") String apiKey,
            @Value("${envoy.llm.base-url:}") String baseUrl) {
        var builder = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .maxRetries(0);
        if (!baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }

    /**
     * Wraps the SDK client with model and token defaults for envoy.
     *
     * @param client    the shared Anthropic SDK client
     * @param model     model id ({@code envoy.llm.model})
     * @param maxTokens response length cap ({@code envoy.llm.max-tokens})
     * @return the wrapped message client
     */
    @Bean
    public AnthropicMessageClient anthropicMessageClient(
            AnthropicClient client,
            @Value("${envoy.llm.model:claude-sonnet-4-6}") String model,
            @Value("${envoy.llm.max-tokens:4096}") int maxTokens) {
        return new AnthropicMessageClient(client, model, maxTokens);
    }

    /**
     * Wraps the SDK client for Librarian's shelf-photo reads. Separate from the
     * Envoy client so the two do not share a circuit breaker.
     *
     * @param client    the SDK client
     * @param model     model id ({@code librarian.llm.model})
     * @param maxTokens response length cap ({@code librarian.llm.max-tokens})
     * @return the vision client
     */
    @Bean
    public AnthropicVisionClient anthropicVisionClient(
            AnthropicClient client,
            @Value("${librarian.llm.model:claude-opus-5}") String model,
            @Value("${librarian.llm.max-tokens:8192}") int maxTokens) {
        return new AnthropicVisionClient(client, model, maxTokens);
    }
}
