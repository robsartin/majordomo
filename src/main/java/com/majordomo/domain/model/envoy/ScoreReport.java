package com.majordomo.domain.model.envoy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable scoring output. One {@code ScoreReport} is produced per (posting, rubric-version)
 * pair. All LLM rationale is preserved so historical decisions remain auditable after the
 * rubric evolves.
 *
 * <p>The {@code usage} field carries provider-supplied call metadata (token counts and
 * wall-clock latency) captured by the LLM adapter. It is reused from
 * {@link LlmScoreResponse.Usage} rather than introduced as a separate domain record because
 * (1) both records live in the same domain package, so there is no cross-layer dependency
 * concern; (2) the shape is identical (three non-negative {@code long} fields) and there is
 * no semantic divergence today; (3) duplicating the type would force a tiny, mechanical
 * mapping with no behavioural value. If the two ever diverge (for example, persisted
 * reports gaining a cost-in-cents field that the LLM port does not know about), the type
 * can be split at that point.
 *
 * @param id              UUIDv7 assigned at persist time
 * @param organizationId  the owning org (denormalized from the posting for query speed)
 * @param postingId       the {@link JobPosting} scored
 * @param rubricId        the {@link Rubric} used
 * @param rubricVersion   denormalized for easy querying even if rubric rows are retired
 * @param disqualifiedBy  present if a disqualifier fired (forces recommendation SKIP)
 * @param categoryScores  one entry per category in the rubric
 * @param flagHits        any flags the LLM raised
 * @param rawScore        sum of category points (before flag penalties)
 * @param finalScore      rawScore minus sum of flag penalties (floored at 0)
 * @param recommendation  derived from finalScore and rubric thresholds
 * @param llmModel        model identifier used (e.g. "claude-sonnet-4-6") for reproducibility
 * @param scoredAt        timestamp the scoring completed
 * @param usage           optional provider-supplied call metadata; empty when the adapter
 *                        did not capture usage data (e.g. legacy rows or providers that
 *                        omit token counts)
 */
public record ScoreReport(
        UUID id,
        UUID organizationId,
        UUID postingId,
        UUID rubricId,
        int rubricVersion,
        Optional<Disqualifier> disqualifiedBy,
        List<CategoryScore> categoryScores,
        List<FlagHit> flagHits,
        int rawScore,
        int finalScore,
        Recommendation recommendation,
        String llmModel,
        Instant scoredAt,
        Optional<LlmScoreResponse.Usage> usage
) {

    /**
     * Convenience constructor for callers that have no usage data to attach.
     * Equivalent to supplying {@link Optional#empty()} for {@code usage}.
     *
     * @param id              see {@link #id()}
     * @param organizationId  see {@link #organizationId()}
     * @param postingId       see {@link #postingId()}
     * @param rubricId        see {@link #rubricId()}
     * @param rubricVersion   see {@link #rubricVersion()}
     * @param disqualifiedBy  see {@link #disqualifiedBy()}
     * @param categoryScores  see {@link #categoryScores()}
     * @param flagHits        see {@link #flagHits()}
     * @param rawScore        see {@link #rawScore()}
     * @param finalScore      see {@link #finalScore()}
     * @param recommendation  see {@link #recommendation()}
     * @param llmModel        see {@link #llmModel()}
     * @param scoredAt        see {@link #scoredAt()}
     */
    public ScoreReport(
            UUID id,
            UUID organizationId,
            UUID postingId,
            UUID rubricId,
            int rubricVersion,
            Optional<Disqualifier> disqualifiedBy,
            List<CategoryScore> categoryScores,
            List<FlagHit> flagHits,
            int rawScore,
            int finalScore,
            Recommendation recommendation,
            String llmModel,
            Instant scoredAt) {
        this(id, organizationId, postingId, rubricId, rubricVersion,
                disqualifiedBy, categoryScores, flagHits, rawScore, finalScore,
                recommendation, llmModel, scoredAt, Optional.empty());
    }
}
