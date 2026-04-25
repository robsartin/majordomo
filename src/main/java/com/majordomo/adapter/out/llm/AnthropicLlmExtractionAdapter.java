package com.majordomo.adapter.out.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.majordomo.application.envoy.LlmScoringException;
import com.majordomo.domain.port.out.envoy.LlmExtractionPort;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Uses the shared {@link AnthropicMessageClient} to ask the LLM to pull structured
 * fields out of raw job-posting HTML or text.
 */
@Component
public class AnthropicLlmExtractionAdapter implements LlmExtractionPort {

    private static final String SYSTEM_PROMPT = """
            You will be given the raw body of a job posting (HTML or plain text).
            Return a JSON object with best-effort values for any of these keys you
            can determine: company, title, location, salary, equity, team, tech.
            Omit keys you cannot determine. Return JSON only, no prose, no fences.
            """;

    private final AnthropicMessageClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Constructs the adapter.
     *
     * @param client wrapper over the Anthropic SDK client
     */
    public AnthropicLlmExtractionAdapter(AnthropicMessageClient client) {
        this.client = client;
    }

    @Override
    public Map<String, String> extract(String rawText) {
        String json = client.send(SYSTEM_PROMPT, rawText);
        try {
            return mapper.readValue(json, new TypeReference<Map<String, String>>() { });
        } catch (Exception e) {
            throw new LlmScoringException("Extraction LLM returned unparseable JSON: " + json, e);
        }
    }
}
