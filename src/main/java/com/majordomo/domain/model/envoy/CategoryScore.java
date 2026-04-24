package com.majordomo.domain.model.envoy;

/**
 * The LLM's verdict for a single category. The rationale is preserved verbatim
 * for audit so surprising scores can be explained after the fact.
 *
 * @param categoryKey the {@link Category#key()} this score applies to
 * @param points      points awarded (matches the selected tier's points)
 * @param tierLabel   label of the {@link Tier} the LLM selected
 * @param rationale   LLM's free-text reasoning for the tier selection
 */
public record CategoryScore(String categoryKey, int points, String tierLabel, String rationale) { }
