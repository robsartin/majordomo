package com.majordomo.domain.port.in.envoy;

import com.majordomo.domain.model.envoy.ScoreReport;

import java.util.List;
import java.util.UUID;

/** Inbound port for scoring a previously-ingested posting against active rubrics. */
public interface ScoreJobPostingUseCase {

    /**
     * Scores a posting using the active rubric for {@code (organizationId, rubricName)}.
     *
     * @param postingId      the posting to score
     * @param rubricName     rubric name (e.g. "default")
     * @param organizationId the requesting org (must own the posting)
     * @return the persisted score report
     */
    ScoreReport score(UUID postingId, String rubricName, UUID organizationId);

    /**
     * Scores a posting against multiple rubrics in one operation. Each rubric
     * produces a separate persisted {@link ScoreReport} and a separate
     * {@code JobPostingScored} domain event. Fails fast: if any rubric (or the
     * posting itself) cannot be resolved, the call throws and no reports are
     * persisted and no events are published.
     *
     * @param postingId      the posting to score
     * @param rubricNames    rubric names to score against (must be non-empty)
     * @param organizationId the requesting org (must own the posting)
     * @return the persisted score reports, in the same order as {@code rubricNames}
     * @throws IllegalArgumentException if {@code rubricNames} is empty
     */
    List<ScoreReport> scoreAll(UUID postingId, List<String> rubricNames, UUID organizationId);
}
