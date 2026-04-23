package com.majordomo.domain.model.envoy;

/**
 * A hard disqualifier. If the LLM finds evidence of a disqualifier in the posting,
 * the recommendation is automatically {@code SKIP} regardless of raw score.
 *
 * @param key         stable identifier (e.g. "ON_SITE_ONLY"); referenced in score reports
 * @param description natural-language criteria read by the LLM
 */
public record Disqualifier(String key, String description) { }
