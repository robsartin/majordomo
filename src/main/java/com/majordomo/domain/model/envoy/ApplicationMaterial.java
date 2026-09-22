package com.majordomo.domain.model.envoy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * One generated draft (ADR-0028). Immutable, and never overwritten: a
 * regeneration adds a record rather than replacing one, because a draft that
 * was actually sent to a company is a historical fact and "what did I tell
 * them?" has to stay answerable.
 *
 * <p>Shaped after {@link ScoreReport}, which already solved org scoping, usage
 * capture and historical reproducibility for the same problem.
 *
 * @param id             UUIDv7 assigned at persist time
 * @param organizationId the owning org
 * @param postingId      the posting this was written for
 * @param scoreReportId  the report whose rationale informed it, when there was
 *                       one; a draft can be written for a posting that has not
 *                       been scored
 * @param kind           what was drafted
 * @param tone           the register requested
 * @param content        the draft itself
 * @param claims         the factual claims made, each with its source span
 * @param llmModel       model identifier, for reproducibility
 * @param generatedAt    when generation completed
 * @param usage          provider-supplied call metadata where the adapter
 *                       captured it
 */
public record ApplicationMaterial(
        UUID id,
        UUID organizationId,
        UUID postingId,
        Optional<UUID> scoreReportId,
        MaterialKind kind,
        Tone tone,
        String content,
        List<ClaimCitation> claims,
        String llmModel,
        Instant generatedAt,
        Optional<LlmScoreResponse.Usage> usage
) {

    /**
     * Validates and defensively copies.
     *
     * @throws IllegalArgumentException if the content is blank
     */
    public ApplicationMaterial {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("A generated material must have content");
        }
        // Copied, not referenced. Otherwise the caller's list stays live inside
        // a record of what was claimed, and the record could change after the
        // draft was sent — the one thing it exists to prevent.
        claims = claims == null ? List.of() : List.copyOf(claims);
    }
}
