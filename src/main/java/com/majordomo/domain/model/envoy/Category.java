package com.majordomo.domain.model.envoy;

import java.util.List;

/**
 * A scoring category. Each category has a cap ({@code maxPoints}) and an ordered
 * list of {@link Tier}s the LLM chooses from. The highest-points tier should be
 * listed first for readability.
 *
 * @param key         stable identifier (e.g. "compensation")
 * @param description natural-language description read by the LLM
 * @param maxPoints   upper bound on the points any tier in this category awards
 * @param tiers       ordered tiers (highest-scoring first)
 */
public record Category(String key, String description, int maxPoints, List<Tier> tiers) { }
