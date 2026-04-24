package com.majordomo.adapter.out.persistence.envoy;

import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.port.out.envoy.ScoreReportRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory {@link ScoreReportRepository} for the Phase-1 vertical slice. */
@Repository
@Profile("envoy-memory")
public class InMemoryScoreReportRepository implements ScoreReportRepository {

    private final Map<UUID, ScoreReport> byId = new ConcurrentHashMap<>();

    @Override
    public ScoreReport save(ScoreReport report) {
        byId.put(report.id(), report);
        return report;
    }

    @Override
    public Optional<ScoreReport> findById(UUID id, UUID organizationId) {
        return Optional.ofNullable(byId.get(id))
                .filter(r -> Objects.equals(r.organizationId(), organizationId));
    }

    @Override
    public Page<ScoreReport> query(UUID organizationId, Integer minFinalScore,
                                   Recommendation recommendation, UUID cursor, int limit) {
        int clamped = Math.max(1, Math.min(limit, 100));
        var items = byId.values().stream()
                .filter(r -> Objects.equals(r.organizationId(), organizationId))
                .filter(r -> minFinalScore == null || r.finalScore() >= minFinalScore)
                .filter(r -> recommendation == null || r.recommendation() == recommendation)
                .sorted(Comparator.comparing(ScoreReport::id))
                .dropWhile(r -> cursor != null && r.id().compareTo(cursor) <= 0)
                .limit(clamped + 1L)
                .toList();
        return Page.fromOverfetch(items, limit, ScoreReport::id);
    }
}
