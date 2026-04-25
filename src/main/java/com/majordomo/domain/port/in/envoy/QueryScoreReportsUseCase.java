package com.majordomo.domain.port.in.envoy;

import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.envoy.ScoreReport;

import java.util.Optional;
import java.util.UUID;

/** Inbound port for reading score reports. All operations are org-scoped. */
public interface QueryScoreReportsUseCase {

    /**
     * Finds a report by id within an org.
     *
     * @param id             report id
     * @param organizationId owning org
     * @return matching report, or empty
     */
    Optional<ScoreReport> findById(UUID id, UUID organizationId);

    /**
     * Cursor-paginated, filterable report query within an org.
     *
     * @param organizationId required org scope
     * @param minFinalScore  optional min score (null = no lower bound)
     * @param recommendation optional recommendation filter (null = any)
     * @param cursor         optional cursor (null = first page)
     * @param limit          row cap (clamped to [1, 100])
     * @return page of reports
     */
    Page<ScoreReport> query(UUID organizationId, Integer minFinalScore,
                            Recommendation recommendation, UUID cursor, int limit);
}
