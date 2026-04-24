package com.majordomo.adapter.out.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.majordomo.application.envoy.LlmScoringException;
import com.majordomo.application.envoy.PromptBuilder;
import com.majordomo.application.envoy.ScoringPrompt;
import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.LlmScoreResponse;
import com.majordomo.domain.model.envoy.Rubric;
import com.majordomo.domain.port.out.envoy.LlmScoringPort;
import org.springframework.stereotype.Component;

/**
 * Scores postings via the Anthropic SDK with a rubric-derived system prompt and
 * the posting as the user message. Delegates prompt rendering to
 * {@link PromptBuilder} and SDK plumbing to {@link AnthropicMessageClient}.
 */
@Component
public class AnthropicLlmScoringAdapter implements LlmScoringPort {

    private final AnthropicMessageClient client;
    private final PromptBuilder promptBuilder;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Constructs the adapter.
     *
     * @param client        wrapper over the Anthropic SDK client
     * @param promptBuilder renders system + user prompts from rubric + posting
     */
    public AnthropicLlmScoringAdapter(AnthropicMessageClient client, PromptBuilder promptBuilder) {
        this.client = client;
        this.promptBuilder = promptBuilder;
    }

    @Override
    public LlmScoreResponse score(JobPosting posting, Rubric rubric) {
        ScoringPrompt prompt = promptBuilder.build(posting, rubric);
        String json = client.send(prompt.systemPrompt(), prompt.userPrompt());
        try {
            return mapper.readValue(json, LlmScoreResponse.class);
        } catch (Exception e) {
            throw new LlmScoringException("LLM returned unparseable JSON: " + json, e);
        }
    }

    @Override
    public String modelId() {
        return client.model();
    }
}
