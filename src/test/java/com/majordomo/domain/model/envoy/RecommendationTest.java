package com.majordomo.domain.model.envoy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Recommendation#fromScore(int, Thresholds)} — the static factory
 * that maps a final score against thresholds. Boundary conditions are exhaustive
 * because the ladder is otherwise easy to off-by-one.
 */
class RecommendationTest {

    private static final Thresholds T = new Thresholds(85, 70, 50);

    /** A score at or above applyImmediately is APPLY_NOW. */
    @Test
    void applyNowAtOrAboveApplyImmediatelyThreshold() {
        assertThat(Recommendation.fromScore(85, T)).isEqualTo(Recommendation.APPLY_NOW);
        assertThat(Recommendation.fromScore(100, T)).isEqualTo(Recommendation.APPLY_NOW);
    }

    /** A score in [apply, applyImmediately) is APPLY. */
    @Test
    void applyBetweenApplyAndApplyImmediately() {
        assertThat(Recommendation.fromScore(70, T)).isEqualTo(Recommendation.APPLY);
        assertThat(Recommendation.fromScore(84, T)).isEqualTo(Recommendation.APPLY);
    }

    /** A score in [considerOnly, apply) is CONSIDER. */
    @Test
    void considerBetweenConsiderOnlyAndApply() {
        assertThat(Recommendation.fromScore(50, T)).isEqualTo(Recommendation.CONSIDER);
        assertThat(Recommendation.fromScore(69, T)).isEqualTo(Recommendation.CONSIDER);
    }

    /** A score below considerOnly is SKIP. */
    @Test
    void skipBelowConsiderOnly() {
        assertThat(Recommendation.fromScore(49, T)).isEqualTo(Recommendation.SKIP);
        assertThat(Recommendation.fromScore(0, T)).isEqualTo(Recommendation.SKIP);
    }
}
