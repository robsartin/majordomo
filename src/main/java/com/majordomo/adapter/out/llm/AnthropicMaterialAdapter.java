package com.majordomo.adapter.out.llm;

import com.majordomo.application.envoy.LlmScoringException;
import com.majordomo.application.envoy.MaterialPrompt;
import com.majordomo.application.envoy.MaterialPromptBuilder;
import com.majordomo.domain.model.envoy.LlmMaterialResponse;
import com.majordomo.domain.model.envoy.MaterialBrief;
import com.majordomo.domain.port.out.envoy.LlmMaterialPort;

import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * Drafts application materials via the Anthropic SDK (#350, ADR-0028).
 *
 * <p>Parsing only. Nothing here decides whether the draft is acceptable — the
 * grounding check runs in the application service, on the parsed claims, so an
 * adapter cannot quietly let something through.
 */
@Component
public class AnthropicMaterialAdapter implements LlmMaterialPort {

    private final AnthropicMaterialClient client;
    private final MaterialPromptBuilder promptBuilder;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Constructs the adapter.
     *
     * @param client        the drafting client
     * @param promptBuilder renders the prompts
     */
    public AnthropicMaterialAdapter(
            AnthropicMaterialClient client, MaterialPromptBuilder promptBuilder) {
        this.client = client;
        this.promptBuilder = promptBuilder;
    }

    @Override
    public LlmMaterialResponse generate(MaterialBrief brief) {
        MaterialPrompt prompt = promptBuilder.build(brief);
        AnthropicMessageClient.MessageResult result =
                client.sendWithUsage(prompt.systemPrompt(), prompt.userPrompt());
        LlmMaterialResponse parsed;
        try {
            parsed = mapper.readValue(result.text(), LlmMaterialResponse.class);
        } catch (Exception e) {
            throw new LlmScoringException(
                    "LLM returned unparseable JSON: " + result.text(), e);
        }
        return result.usage().map(parsed::withUsage).orElse(parsed);
    }

    @Override
    public String modelId() {
        return client.model();
    }
}
