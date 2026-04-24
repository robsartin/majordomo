package com.majordomo.application.envoy;

/**
 * A pair of prompts built from a rubric and posting. The system prompt is stable
 * per rubric version and a prime prompt-cache candidate when many postings are
 * scored against the same rubric.
 *
 * @param systemPrompt rubric-derived instructions and schema
 * @param userPrompt   the posting, formatted for the LLM
 */
public record ScoringPrompt(String systemPrompt, String userPrompt) { }
