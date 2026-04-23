package com.majordomo.domain.model.envoy;

/**
 * A scoring tier within a {@link Category}. Tiers are ordered within a category
 * from highest to lowest points; the LLM picks exactly one tier per category.
 *
 * @param label    human-readable tier name (e.g. "Excellent", "Acceptable")
 * @param points   points awarded when this tier is selected
 * @param criteria description of when this tier applies — read by the LLM
 */
public record Tier(String label, int points, String criteria) { }
