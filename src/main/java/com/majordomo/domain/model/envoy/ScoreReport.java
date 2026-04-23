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
        Instant scoredAt
) { }
