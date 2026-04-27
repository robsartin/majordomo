package com.majordomo.domain.model.envoy;

/**
 * The LLM's self-reported certainty for a single {@link CategoryScore}.
 * Surfaced on score-report views so users can tell a confident verdict
 * (explicit signals in the posting) from a guess (sparse or ambiguous text).
 *
 * <p>Three buckets keep the LLM contract simple — finer gradations would
 * invite false precision from a model that has no grounded notion of
 * probability. Mapped to JSON by name (Jackson default for enums).
 */
public enum Confidence {
    /** The posting contains explicit, unambiguous evidence for the chosen tier. */
    HIGH,
    /** Some evidence is present but partial, indirect, or open to interpretation. */
    MEDIUM,
    /** Sparse or absent signals; the verdict is essentially a guess. */
    LOW
}
