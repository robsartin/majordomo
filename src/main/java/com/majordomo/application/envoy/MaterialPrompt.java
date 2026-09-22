package com.majordomo.application.envoy;

/**
 * A rendered system and user prompt for drafting (#350).
 *
 * @param systemPrompt the rules, identical for every draft and therefore
 *                     worth caching
 * @param userPrompt   the posting, rationale and résumé for this one
 */
public record MaterialPrompt(String systemPrompt, String userPrompt) { }
