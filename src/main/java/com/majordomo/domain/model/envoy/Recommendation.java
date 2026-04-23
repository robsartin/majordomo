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
    SKIP
}
