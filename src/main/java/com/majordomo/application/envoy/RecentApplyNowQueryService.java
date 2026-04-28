package com.majordomo.application.envoy;

import com.majordomo.domain.model.envoy.ApplyNowConversionStat;
import com.majordomo.domain.model.envoy.ApplyNowPosting;
import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.port.in.envoy.GetApplyNowConversionStatUseCase;
import com.majordomo.domain.port.in.envoy.GetRecentApplyNowPostingsUseCase;
import com.majordomo.domain.port.out.envoy.JobPostingRepository;
import com.majordomo.domain.port.out.envoy.ScoreReportRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Lists recent APPLY_NOW reports enriched with posting metadata, plus the
 * "X of Y applied" rollup for the same window. Cached in {@code envoy-apply-now}
 * (postings) and {@code envoy-apply-now-stat} (rollup); both evicted on
 * {@link com.majordomo.domain.model.event.JobPostingScored},
 * {@link com.majordomo.domain.model.event.PostingMarkedApplied}, and
 * {@link com.majordomo.domain.model.event.PostingDismissed} so newly-scored or
 * newly-converted postings show up promptly on summary surfaces.
 */
@Service
public class RecentApplyNowQueryService
        implements GetRecentApplyNowPostingsUseCase, GetApplyNowConversionStatUseCase {

    /** Window used by both the dashboard panel and the /envoy stat. */
    static final int STAT_WINDOW = 50;

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

    @Override
    @Cacheable(value = "envoy-apply-now-stat", key = "#organizationId")
    public ApplyNowConversionStat getStat(UUID organizationId) {
        var page = reports.query(organizationId, null, Recommendation.APPLY_NOW, null, STAT_WINDOW);
        long total = page.items().size();
        if (total == 0) {
            return ApplyNowConversionStat.EMPTY;
        }
        long applied = page.items().stream()
                .map(r -> postings.findById(r.postingId(), organizationId).orElse(null))
                .filter(p -> p != null && p.getAppliedAt() != null)
                .count();
        return new ApplyNowConversionStat(total, applied);
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
