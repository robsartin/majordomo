package com.majordomo.domain.model.envoy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;

/**
 * The LLM's verdict for a single category. The rationale is preserved verbatim
 * for audit so surprising scores can be explained after the fact.
 *
 * <p>{@code confidence} is optional on the wire: legacy reports written before
 * issue #150 lack the field, and {@link ScoreReport ScoreReport} bodies are
 * loaded via Jackson from the JSONB column on every read.
 *
 * @param categoryKey the {@link Category#key()} this score applies to
 * @param points      points awarded (matches the selected tier's points)
 * @param tierLabel   label of the {@link Tier} the LLM selected
 * @param rationale   LLM's free-text reasoning for the tier selection
 * @param confidence  the LLM's self-reported certainty; empty for legacy rows
 */
public record CategoryScore(
        String categoryKey,
        int points,
        String tierLabel,
        String rationale,
        Optional<Confidence> confidence) {

    /**
     * Convenience constructor for callers that did not capture confidence
     * (e.g. older test fixtures). Equivalent to {@link Optional#empty()}.
     *
     * @param categoryKey the {@link Category#key()} this score applies to
     * @param points      points awarded (matches the selected tier's points)
     * @param tierLabel   label of the {@link Tier} the LLM selected
     * @param rationale   LLM's free-text reasoning for the tier selection
     */
    public CategoryScore(String categoryKey, int points, String tierLabel, String rationale) {
        this(categoryKey, points, tierLabel, rationale, Optional.empty());
    }

    /**
     * Jackson creator so a missing or null {@code confidence} field
     * deserialises to {@link Optional#empty()} — required for backward
     * compatibility with score-report JSONB rows written prior to #150.
     *
     * @param categoryKey raw category key from JSON
     * @param points      raw point value from JSON
     * @param tierLabel   raw tier label from JSON
     * @param rationale   raw rationale from JSON
     * @param confidence  raw confidence enum (possibly null) from JSON
     * @return a normalised {@code CategoryScore}
     */
    @JsonCreator
    public static CategoryScore of(
            @JsonProperty("categoryKey") String categoryKey,
            @JsonProperty("points") int points,
            @JsonProperty("tierLabel") String tierLabel,
            @JsonProperty("rationale") String rationale,
            @JsonProperty("confidence") Confidence confidence) {
        return new CategoryScore(
                categoryKey, points, tierLabel, rationale, Optional.ofNullable(confidence));
    }
}
