package com.majordomo.application.envoy;

import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.envoy.ApplyNowPosting;
import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.port.out.envoy.JobPostingRepository;
import com.majordomo.domain.port.out.envoy.ScoreReportRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link RecentApplyNowQueryService}.
 */
class RecentApplyNowQueryServiceTest {

    private final ScoreReportRepository reports = mock(ScoreReportRepository.class);
    private final JobPostingRepository postings = mock(JobPostingRepository.class);
    private final RecentApplyNowQueryService service = new RecentApplyNowQueryService(reports, postings);

    /** Delegates to the report repo with recommendation = APPLY_NOW and enriches each row from the posting repo. */
    @Test
    void enrichesApplyNowReportsWithPostingMetadata() {
        UUID orgId = UUID.randomUUID();
        UUID postingId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        ScoreReport report = report(reportId, orgId, postingId, 92);
        when(reports.query(eq(orgId), isNull(), eq(Recommendation.APPLY_NOW), isNull(), eq(5)))
                .thenReturn(new Page<>(List.of(report), null, false));
        when(postings.findById(postingId, orgId)).thenReturn(Optional.of(posting(postingId, orgId,
                "Acme Inc", "Senior Engineer", "Remote")));

        List<ApplyNowPosting> result = service.getRecentApplyNow(orgId, 5);

        assertThat(result).containsExactly(new ApplyNowPosting(
                reportId, postingId, "Acme Inc", "Senior Engineer", "Remote", 92));
    }

    /** A missing posting (rare race / hard-deleted) is tolerated: the row still appears with null enrichment. */
    @Test
    void tolerantOfMissingPosting() {
        UUID orgId = UUID.randomUUID();
        UUID postingId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        when(reports.query(eq(orgId), isNull(), eq(Recommendation.APPLY_NOW), isNull(), eq(5)))
                .thenReturn(new Page<>(List.of(report(reportId, orgId, postingId, 90)), null, false));
        when(postings.findById(postingId, orgId)).thenReturn(Optional.empty());

        List<ApplyNowPosting> result = service.getRecentApplyNow(orgId, 5);

        assertThat(result).containsExactly(new ApplyNowPosting(
                reportId, postingId, null, null, null, 90));
    }

    /** Limit is clamped to [1, 100]. */
    @Test
    void clampsLimit() {
        UUID orgId = UUID.randomUUID();
        when(reports.query(eq(orgId), isNull(), eq(Recommendation.APPLY_NOW), isNull(), eq(1)))
                .thenReturn(new Page<>(List.of(), null, false));

        service.getRecentApplyNow(orgId, 0);

        verify(reports).query(eq(orgId), isNull(), eq(Recommendation.APPLY_NOW), isNull(), eq(1));
    }

    /** Limit above 100 is clamped down. */
    @Test
    void clampsHighLimit() {
        UUID orgId = UUID.randomUUID();
        when(reports.query(eq(orgId), isNull(), eq(Recommendation.APPLY_NOW), isNull(), eq(100)))
                .thenReturn(new Page<>(List.of(), null, false));

        service.getRecentApplyNow(orgId, 5000);

        verify(reports).query(eq(orgId), isNull(), eq(Recommendation.APPLY_NOW), isNull(), eq(100));
    }

    private static ScoreReport report(UUID id, UUID orgId, UUID postingId, int finalScore) {
        return new ScoreReport(
                id, orgId, postingId, UUID.randomUUID(), 1,
                Optional.empty(), List.of(), List.of(),
                finalScore, finalScore, Recommendation.APPLY_NOW,
                "claude-test", Instant.parse("2026-04-01T00:00:00Z"));
    }

    private static JobPosting posting(UUID id, UUID orgId, String company, String title, String location) {
        JobPosting p = new JobPosting();
        p.setId(id);
        p.setOrganizationId(orgId);
        p.setSource("manual");
        p.setRawText("body");
        p.setCompany(company);
        p.setTitle(title);
        p.setLocation(location);
        return p;
    }
}
