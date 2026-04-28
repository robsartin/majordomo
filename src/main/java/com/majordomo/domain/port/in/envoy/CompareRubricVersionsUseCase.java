package com.majordomo.domain.port.in.envoy;

import com.majordomo.domain.model.envoy.RubricComparison;

import java.util.UUID;

/**
 * Inbound port for rubric version A/B comparison. Re-runs scoring against two
 * arbitrary versions of the same rubric over the most-recent N postings; the
 * resulting reports are NOT persisted (live {@code ScoreReport} rows are
 * untouched).
 */
public interface CompareRubricVersionsUseCase {

    /**
     * Compares two versions of {@code rubricName} on the most-recent {@code limit}
     * postings in {@code organizationId}. Both versions are scored fresh against
     * each posting; results are aggregated in memory and returned.
     *
     * @param rubricName     the rubric to compare (e.g. "default")
     * @param fromVersion    older version number
     * @param toVersion      newer version number
     * @param limit          number of most-recent postings to score (clamped to [1, 50])
     * @param organizationId owning org
     * @return aggregated comparison
     * @throws IllegalArgumentException if either version is missing or {@code from == to}
     */
    RubricComparison compare(String rubricName,
                             int fromVersion,
                             int toVersion,
                             int limit,
                             UUID organizationId);
}
