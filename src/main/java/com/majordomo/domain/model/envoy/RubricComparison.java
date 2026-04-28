package com.majordomo.domain.model.envoy;

import java.util.List;
import java.util.Map;

/**
 * Result of running rubric version A/B comparison: per-posting deltas plus
 * aggregate stats. Comparison-only — these reports are NOT persisted to
 * {@code envoy_score_report}; the live report for the current rubric version
 * remains untouched.
 *
 * @param rubricName     the rubric being compared (e.g. "default")
 * @param fromVersion    the older version number
 * @param toVersion      the newer version number
 * @param postings       per-posting comparison rows, in fetched-at-desc order
 * @param meanFromScore  mean final score across the posting set under {@code fromVersion}
 * @param meanToScore    mean final score across the posting set under {@code toVersion}
 * @param fromDistribution recommendation counts under {@code fromVersion}
 * @param toDistribution   recommendation counts under {@code toVersion}
 * @param flips          count of postings whose recommendation differed between versions
 */
public record RubricComparison(
        String rubricName,
        int fromVersion,
        int toVersion,
        List<PostingComparison> postings,
        double meanFromScore,
        double meanToScore,
        Map<Recommendation, Long> fromDistribution,
        Map<Recommendation, Long> toDistribution,
        long flips) {

    /**
     * One comparison row: a posting and its (in-memory) score under each rubric version.
     *
     * @param posting        the source posting
     * @param fromReport     score report under {@code fromVersion} (in-memory only, not persisted)
     * @param toReport       score report under {@code toVersion}   (in-memory only, not persisted)
     * @param scoreDelta     {@code toReport.finalScore() - fromReport.finalScore()}
     * @param recommendationFlipped {@code true} when the recommendation differs between versions
     */
    public record PostingComparison(
            JobPosting posting,
            ScoreReport fromReport,
            ScoreReport toReport,
            int scoreDelta,
            boolean recommendationFlipped) { }
}
