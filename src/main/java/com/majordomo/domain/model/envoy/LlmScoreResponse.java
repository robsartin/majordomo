package com.majordomo.domain.model.envoy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Optional;

/**
 * Raw LLM output contract. The LLM returns tier <em>selections</em> and flag
 * <em>findings</em>; Java looks up the actual point values from the rubric.
 * This prevents the LLM from inventing point values that exceed category caps.
 *
 * @param disqualifierKey  present iff a disqualifier fired; must match a
 *                         {@link Disqualifier#key()} in the active rubric
 * @param categoryVerdicts one entry per category in the rubric (LLM must cover all)
 * @param flagHits         zero or more flags the LLM judged to have fired
 */
public record LlmScoreResponse(
        Optional<String> disqualifierKey,
        List<CategoryVerdict> categoryVerdicts,
        List<FlagFinding> flagHits
) {
    /**
     * A single category verdict returned by the LLM.
     *
     * <p>{@code confidence} is optional on the wire so older score reports
     * (written before issue #150) still deserialise cleanly. Jackson maps the
     * {@link Confidence} enum to/from its name (HIGH/MEDIUM/LOW).
     *
     * @param categoryKey matches {@link Category#key()} in the rubric
     * @param tierLabel   matches a {@link Tier#label()} within that category
     * @param rationale   free text explaining the selection
     * @param confidence  the LLM's self-reported certainty for this verdict;
     *                    empty when absent on the wire
     */
    public record CategoryVerdict(
            String categoryKey,
            String tierLabel,
            String rationale,
            Optional<Confidence> confidence) {

        /**
         * Convenience constructor for callers that have not yet adopted the
         * confidence field (older tests, generated payloads). Equivalent to
         * supplying {@link Optional#empty()} for {@code confidence}.
         *
         * @param categoryKey matches {@link Category#key()} in the rubric
         * @param tierLabel   matches a {@link Tier#label()} within that category
         * @param rationale   free text explaining the selection
         */
        public CategoryVerdict(String categoryKey, String tierLabel, String rationale) {
            this(categoryKey, tierLabel, rationale, Optional.empty());
        }

        /**
         * Jackson creator so a missing or null {@code confidence} field
         * deserialises to {@link Optional#empty()} instead of failing.
         *
         * @param categoryKey raw category key from JSON
         * @param tierLabel   raw tier label from JSON
         * @param rationale   raw rationale from JSON
         * @param confidence  raw confidence enum (possibly null) from JSON
         * @return a normalised {@code CategoryVerdict}
         */
        @JsonCreator
        public static CategoryVerdict of(
                @JsonProperty("categoryKey") String categoryKey,
                @JsonProperty("tierLabel") String tierLabel,
                @JsonProperty("rationale") String rationale,
                @JsonProperty("confidence") Confidence confidence) {
            return new CategoryVerdict(
                    categoryKey, tierLabel, rationale, Optional.ofNullable(confidence));
        }
    }

    /**
     * A single flag finding returned by the LLM.
     *
     * @param flagKey   matches {@link Flag#key()} in the rubric
     * @param rationale free text explaining why the flag fired
     */
    public record FlagFinding(String flagKey, String rationale) { }

    /**
     * Jackson creator so nullable {@code disqualifierKey} deserialises to {@code Optional.empty()}.
     *
     * @param disqualifierKey  raw string (possibly null) from JSON
     * @param categoryVerdicts category verdicts list (null coerced to empty)
     * @param flagHits         flag findings list (null coerced to empty)
     * @return a normalised {@code LlmScoreResponse}
     */
    @JsonCreator
    public static LlmScoreResponse of(
            @JsonProperty("disqualifierKey") String disqualifierKey,
            @JsonProperty("categoryVerdicts") List<CategoryVerdict> categoryVerdicts,
            @JsonProperty("flagHits") List<FlagFinding> flagHits) {
        return new LlmScoreResponse(
                Optional.ofNullable(disqualifierKey),
                categoryVerdicts == null ? List.of() : categoryVerdicts,
                flagHits == null ? List.of() : flagHits);
    }
}
