package com.majordomo.application.envoy;

import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.model.envoy.ScoreReportFilter;
import com.majordomo.domain.port.in.envoy.QueryScoreReportsUseCase;
import com.majordomo.domain.port.out.envoy.ScoreReportRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/** Read-only service for score reports. */
@Service
public class ScoreReportQueryService implements QueryScoreReportsUseCase {

    private final ScoreReportRepository repo;

    /**
     * Constructs the service.
     *
     * @param repo outbound report repository
     */
    public ScoreReportQueryService(ScoreReportRepository repo) {
        this.repo = repo;
    }

    @Override
    public Optional<ScoreReport> findById(UUID id, UUID organizationId) {
        return repo.findById(id, organizationId);
    }

    @Override
    public Page<ScoreReport> query(UUID organizationId, ScoreReportFilter filter,
                                   UUID cursor, int limit) {
        int clamped = Math.max(1, Math.min(limit, 100));
        return repo.query(organizationId, filter, cursor, clamped);
    }
}
