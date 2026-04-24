package com.majordomo.domain.port.in.envoy;

import com.majordomo.domain.model.envoy.ScoreReport;

import java.util.UUID;

/** Inbound port for scoring a previously-ingested posting against an active rubric. */
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
}
