package com.majordomo.domain.model.envoy;

/**
 * Optional filters applied to a {@code ScoreReportRepository.query} call.
 * Each field is nullable; {@code null} means "no filter on that field".
 *
 * <p>Use {@link #none()} for an unfiltered query and the {@code with*}
 * helpers to build narrower filters fluently.</p>
 *
 * @param minFinalScore  include only reports with {@code finalScore >= this} (null = no minimum)
 * @param recommendation include only reports with this recommendation (null = any)
 */
public record ScoreReportFilter(Integer minFinalScore, Recommendation recommendation) {

    private static final ScoreReportFilter NONE = new ScoreReportFilter(null, null);

    /**
     * Returns the no-op filter.
     *
     * @return the singleton "no filter" instance
     */
    public static ScoreReportFilter none() {
        return NONE;
    }

    /**
     * Returns a filter narrowed to the given recommendation tier.
     *
     * @param recommendation required recommendation tier
     * @return a filter where only that tier passes
     */
    public static ScoreReportFilter withRecommendation(Recommendation recommendation) {
        return new ScoreReportFilter(null, recommendation);
    }
}
