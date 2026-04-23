package com.majordomo.domain.model.envoy;

/**
 * A flag the LLM judged to have fired on a posting, with its penalty.
 *
 * @param flagKey   the {@link Flag#key()} that fired
 * @param penalty   points subtracted from the raw score (mirrors {@link Flag#penalty()})
 * @param rationale LLM's reasoning for raising this flag
 */
public record FlagHit(String flagKey, int penalty, String rationale) { }
