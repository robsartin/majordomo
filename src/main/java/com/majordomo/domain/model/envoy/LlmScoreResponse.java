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
     * @param categoryKey matches {@link Category#key()} in the rubric
     * @param tierLabel   matches a {@link Tier#label()} within that category
     * @param rationale   free text explaining the selection
     */
    public record CategoryVerdict(String categoryKey, String tierLabel, String rationale) { }

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
