package com.majordomo.adapter.out.persistence.envoy;

import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.model.envoy.ScoreReportFilter;
import com.majordomo.domain.port.out.envoy.ScoreReportRepository;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JPA-backed adapter for {@link ScoreReportRepository}. */
@Repository
public class ScoreReportRepositoryAdapter implements ScoreReportRepository {

    private final JpaScoreReportRepository jpa;

    /**
     * Constructs the adapter.
     *
     * @param jpa Spring Data repository for {@link ScoreReportEntity}
     */
    public ScoreReportRepositoryAdapter(JpaScoreReportRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public ScoreReport save(ScoreReport report) {
        return ScoreReportMapper.toDomain(jpa.save(ScoreReportMapper.toEntity(report)));
    }

    @Override
    public Optional<ScoreReport> findById(UUID id, UUID organizationId) {
        return jpa.findByIdAndOrganizationId(id, organizationId)
                .map(ScoreReportMapper::toDomain);
    }

    @Override
    public Page<ScoreReport> query(UUID organizationId, ScoreReportFilter filter,
                                   UUID cursor, int limit) {
        int clamped = Math.max(1, Math.min(limit, 100));
        List<ScoreReportEntity> rows = jpa.query(
                organizationId,
                filter.minFinalScore(),
                filter.recommendation() == null ? null : filter.recommendation().name(),
                cursor,
                Sort.by("id").ascending(),
                Limit.of(clamped + 1));
        List<ScoreReport> items = rows.stream().map(ScoreReportMapper::toDomain).toList();
        return Page.fromOverfetch(items, limit, ScoreReport::id);
    }
}
