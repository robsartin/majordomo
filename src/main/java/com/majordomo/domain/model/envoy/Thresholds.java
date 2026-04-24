package com.majordomo.domain.model.envoy;

/**
 * Score thresholds used to derive a {@link Recommendation} from the final score.
 * Values are score cutoffs and must satisfy
 * {@code applyImmediately >= apply >= considerOnly}.
 *
 * @param applyImmediately minimum score for {@code APPLY_NOW}
 * @param apply            minimum score for {@code APPLY}
 * @param considerOnly     minimum score for {@code CONSIDER}; below this is {@code SKIP}
 */
public record Thresholds(int applyImmediately, int apply, int considerOnly) { }
