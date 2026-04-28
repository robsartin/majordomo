package com.majordomo.application.envoy;

import com.majordomo.domain.model.envoy.ApplyNowPosting;
import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.port.in.envoy.GetRecentApplyNowPostingsUseCase;
import com.majordomo.domain.port.out.envoy.JobPostingRepository;
import com.majordomo.domain.port.out.envoy.ScoreReportRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Lists recent APPLY_NOW reports enriched with posting metadata for summary surfaces.
 * Cached in {@code envoy-apply-now} (5-minute TTL via {@link com.majordomo.adapter.in.web.config.CacheConfig});
 * the cache is evicted on {@link com.majordomo.domain.model.event.JobPostingScored} so newly-scored APPLY_NOW
 * postings appear promptly on the dashboard.
 */
@Service
public class RecentApplyNowQueryService implements GetRecentApplyNowPostingsUseCase {

    private final ScoreReportRepository reports;
    private final JobPostingRepository postings;

    /**
     * Constructs the service.
     *
     * @param reports outbound port for score reports
     * @param postings outbound port for postings (used to enrich each report)
     */
    public RecentApplyNowQueryService(ScoreReportRepository reports, JobPostingRepository postings) {
        this.reports = reports;
        this.postings = postings;
    }

    @Override
    @Cacheable(value = "envoy-apply-now", key = "#organizationId + ':' + #limit")
    public List<ApplyNowPosting> getRecentApplyNow(UUID organizationId, int limit) {
        int clamped = Math.max(1, Math.min(limit, 100));
        var page = reports.query(organizationId, null, Recommendation.APPLY_NOW, null, clamped);
        return page.items().stream().map(report -> enrich(report, organizationId)).toList();
    }

    private ApplyNowPosting enrich(ScoreReport report, UUID organizationId) {
        JobPosting posting = postings.findById(report.postingId(), organizationId).orElse(null);
        return new ApplyNowPosting(
                report.id(),
                report.postingId(),
                posting != null ? posting.getCompany() : null,
                posting != null ? posting.getTitle() : null,
                posting != null ? posting.getLocation() : null,
                report.finalScore());
    }
}
