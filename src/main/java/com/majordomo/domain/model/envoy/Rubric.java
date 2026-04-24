package com.majordomo.domain.model.envoy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A versioned scoring rubric. Rubrics are immutable once persisted; edits produce
 * a new row with {@code version + 1} and a new {@code effectiveFrom}. Score
 * reports reference a specific rubric version so historical scores remain
 * reproducible.
 *
 * <p>{@code organizationId} is {@link Optional#empty()} for the seeded <strong>system
 * default</strong> template (visible to every org). An org-specific rubric has a
 * present {@code organizationId} and shadows the system default when
 * {@code findActiveByName} is called.</p>
 *
 * @param id             UUIDv7 assigned at persist time
 * @param organizationId empty for the system default; present for org-specific rubrics
 * @param version        monotonically increasing per {@code (organizationId, name)}
 * @param name           logical rubric name (e.g. "default")
 * @param disqualifiers  hard disqualifiers — any hit forces recommendation SKIP
 * @param categories     scoring categories the LLM evaluates
 * @param flags          soft penalties applied after category scoring
 * @param thresholds     score cutoffs that map raw score to {@link Recommendation}
 * @param effectiveFrom  timestamp this version became active
 */
public record Rubric(
        UUID id,
        Optional<UUID> organizationId,
        int version,
        String name,
        List<Disqualifier> disqualifiers,
        List<Category> categories,
        List<Flag> flags,
        Thresholds thresholds,
        Instant effectiveFrom
) { }
