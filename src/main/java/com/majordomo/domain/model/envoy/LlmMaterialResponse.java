package com.majordomo.domain.model.envoy;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Optional;

/**
 * Raw LLM output for a drafted material (#350, ADR-0028).
 *
 * <p>The claims are deliberately <em>not</em> {@link ClaimCitation}s here.
 * That type validates, and a model returning a too-short source span would then
 * fail as unparseable JSON — reporting a transport problem for what is really a
 * grounding problem. They are converted after parsing so the failure says what
 * actually went wrong.
 *
 * @param draft  the generated text
 * @param claims the factual claims made, each with the span it came from
 * @param usage  provider metadata, attached by the adapter
 */
public record LlmMaterialResponse(
        @JsonProperty("draft") String draft,
        @JsonProperty("claims") List<RawClaim> claims,
        Optional<LlmScoreResponse.Usage> usage
) {

    /**
     * An unvalidated claim exactly as the model returned it.
     *
     * @param claim  the assertion
     * @param source the span of source text it came from
     */
    public record RawClaim(
            @JsonProperty("claim") String claim,
            @JsonProperty("source") String source) { }

    /**
     * Copies the claim list and defaults the pieces the model may omit.
     */
    public LlmMaterialResponse {
        claims = claims == null ? List.of() : List.copyOf(claims);
        usage = usage == null ? Optional.empty() : usage;
    }

    /**
     * Returns a copy carrying usage metadata.
     *
     * @param measured the usage the adapter captured
     * @return the response with usage attached
     */
    public LlmMaterialResponse withUsage(LlmScoreResponse.Usage measured) {
        return new LlmMaterialResponse(draft, claims, Optional.of(measured));
    }
}
