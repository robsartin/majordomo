package com.majordomo.domain.model.envoy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ScoreReportFilter} factory helpers.
 */
class ScoreReportFilterTest {

    /** none() returns a singleton with both fields null. */
    @Test
    void noneHasBothFieldsNull() {
        var f = ScoreReportFilter.none();
        assertThat(f.minFinalScore()).isNull();
        assertThat(f.recommendation()).isNull();
    }

    /** none() is identity-stable so callers can safely compare with eq(). */
    @Test
    void noneReturnsSameInstance() {
        assertThat(ScoreReportFilter.none()).isSameAs(ScoreReportFilter.none());
    }

    /** withRecommendation builds a filter constrained to the given tier. */
    @Test
    void withRecommendationOnlyBindsRecommendation() {
        var f = ScoreReportFilter.withRecommendation(Recommendation.APPLY_NOW);
        assertThat(f.minFinalScore()).isNull();
        assertThat(f.recommendation()).isEqualTo(Recommendation.APPLY_NOW);
    }
}
