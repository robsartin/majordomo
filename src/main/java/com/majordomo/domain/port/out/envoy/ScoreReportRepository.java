package com.majordomo.domain.port.out.envoy;

import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.envoy.ScoreReport;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for score report persistence and querying.
 */
public interface ScoreReportRepository {

    /**
     * Persists a new score report. Reports are immutable — overwriting is a misuse.
     *
     * @param report the report to persist
     * @return the persisted report
     */
    ScoreReport save(ScoreReport report);

    /**
     * Finds a report by id, scoped to an organization.
     *
     * @param id             the report id
     * @param organizationId org scope
     * @return the report, or empty if not found within that org
     */
    Optional<ScoreReport> findById(UUID id, UUID organizationId);

    /**
     * Cursor-paginated query over reports within an organization, with optional
     * secondary filters. Null filter values mean "no filter on that field".
     *
     * @param organizationId required — reports are always org-scoped
     * @param minFinalScore  include only reports with finalScore &gt;= this (null = no minimum)
     * @param recommendation include only reports with this recommendation (null = any)
     * @param cursor         next-page cursor (null = first page)
     * @param limit          clamped to [1, 100] by the caller; repository must honour limit+1
     * @return a page of reports in the given org matching the filters
     */
    Page<ScoreReport> query(UUID organizationId, Integer minFinalScore,
                            Recommendation recommendation, UUID cursor, int limit);
}
