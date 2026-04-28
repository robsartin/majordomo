package com.majordomo.domain.model.envoy;

/**
 * Final recommendation derived from the score and rubric thresholds.
 */
public enum Recommendation {
    /** Score meets or exceeds the applyImmediately threshold. */
    APPLY_NOW,
    /** Score meets the apply threshold but not applyImmediately. */
    APPLY,
    /** Score meets the considerOnly threshold but not apply. */
    CONSIDER,
    /** Score is below considerOnly, or a disqualifier was hit. */
    SKIP;

    /**
     * Maps a final score against rubric thresholds to a {@link Recommendation}.
     * Disqualifier hits do not pass through this method — callers force
     * {@link #SKIP} directly when a disqualifier fires.
     *
     * @param finalScore the final (post-flag) score
     * @param thresholds the rubric's score cutoffs
     * @return the recommendation tier the score falls into
     */
    public static Recommendation fromScore(int finalScore, Thresholds thresholds) {
        if (finalScore >= thresholds.applyImmediately()) {
            return APPLY_NOW;
        }
        if (finalScore >= thresholds.apply()) {
            return APPLY;
        }
        if (finalScore >= thresholds.considerOnly()) {
            return CONSIDER;
        }
        return SKIP;
    }
}
