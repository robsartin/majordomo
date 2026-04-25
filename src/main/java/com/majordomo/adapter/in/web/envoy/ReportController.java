package com.majordomo.adapter.in.web.envoy;

import com.majordomo.application.identity.OrganizationAccessService;
import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.port.in.envoy.QueryScoreReportsUseCase;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** REST controller for querying persisted score reports. */
@RestController
@RequestMapping("/api/envoy/reports")
@Tag(name = "Envoy", description = "Score report query")
public class ReportController {

    private final QueryScoreReportsUseCase query;
    private final OrganizationAccessService organizationAccessService;

    /**
     * Constructs the controller.
     *
     * @param query                     inbound port for report queries
     * @param organizationAccessService enforces per-org access control
     */
    public ReportController(QueryScoreReportsUseCase query,
                            OrganizationAccessService organizationAccessService) {
        this.query = query;
        this.organizationAccessService = organizationAccessService;
    }

    /**
     * Lists score reports for an org, cursor-paginated with optional filters.
     *
     * @param organizationId required org scope
     * @param minFinalScore  optional min score filter
     * @param recommendation optional recommendation filter
     * @param cursor         optional cursor for pagination
     * @param limit          row cap (default 20, clamped to [1, 100])
     * @return page of reports
     */
    @GetMapping
    public Page<ScoreReport> list(
            @RequestParam UUID organizationId,
            @RequestParam(required = false) Integer minFinalScore,
            @RequestParam(required = false) Recommendation recommendation,
            @RequestParam(required = false) UUID cursor,
            @RequestParam(defaultValue = "20") int limit) {
        organizationAccessService.verifyAccess(organizationId);
        return query.query(organizationId, minFinalScore, recommendation, cursor, limit);
    }

    /**
     * Fetches a single report by id within an org.
     *
     * @param id             report id
     * @param organizationId required org scope
     * @return 200 with the report or 404 if not found in this org
     */
    @GetMapping("/{id}")
    public ResponseEntity<ScoreReport> getById(@PathVariable UUID id,
                                               @RequestParam UUID organizationId) {
        organizationAccessService.verifyAccess(organizationId);
        return query.findById(id, organizationId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
