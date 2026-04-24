package com.majordomo.domain.model.envoy;

/**
 * A soft flag that subtracts points from the raw score when hit. Unlike a
 * {@link Disqualifier}, a flag does not force a SKIP; it only reduces the score.
 *
 * @param key         stable identifier (e.g. "AT_WILL_LANGUAGE")
 * @param description natural-language criteria read by the LLM
 * @param penalty     points subtracted when this flag is hit (positive number)
 */
public record Flag(String key, String description, int penalty) { }
