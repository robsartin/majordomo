package com.majordomo.domain.model.envoy;

import com.majordomo.domain.model.UuidFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreReportTest {

    @Test
    void scoreReport_capturesAllAuditFields() {
        var cs = new CategoryScore("compensation", 15, "Good", "salary band listed at $200-250k");
        var fh = new FlagHit("AT_WILL", 5, "explicit at-will clause in posting");
        var report = new ScoreReport(
                UuidFactory.newId(),
                UuidFactory.newId(),
                UuidFactory.newId(),
                UuidFactory.newId(),
                3,
                Optional.empty(),
                List.of(cs),
                List.of(fh),
                15,
                10,
                Recommendation.CONSIDER,
                "claude-sonnet-4-6",
                Instant.now());

        assertThat(report.rawScore()).isEqualTo(15);
        assertThat(report.finalScore()).isEqualTo(10);
        assertThat(report.recommendation()).isEqualTo(Recommendation.CONSIDER);
        assertThat(report.disqualifiedBy()).isEmpty();
        assertThat(report.categoryScores()).hasSize(1);
    }

    @Test
    void scoreReport_convenienceConstructorDefaultsUsageToEmpty() {
        var report = new ScoreReport(
                UuidFactory.newId(),
                UuidFactory.newId(),
                UuidFactory.newId(),
                UuidFactory.newId(),
                3,
                Optional.empty(),
                List.of(),
                List.of(),
                0,
                0,
                Recommendation.SKIP,
                "claude-sonnet-4-6",
                Instant.now());

        assertThat(report.usage()).isEmpty();
    }

    @Test
    void scoreReport_capturesUsageWhenSupplied() {
        var usage = new LlmScoreResponse.Usage(123L, 45L, 200L);
        var report = new ScoreReport(
                UuidFactory.newId(),
                UuidFactory.newId(),
                UuidFactory.newId(),
                UuidFactory.newId(),
                1,
                Optional.empty(),
                List.of(),
                List.of(),
                0,
                0,
                Recommendation.SKIP,
                "claude-sonnet-4-6",
                Instant.now(),
                Optional.of(usage));

        assertThat(report.usage()).contains(usage);
        assertThat(report.usage().get().inputTokens()).isEqualTo(123L);
        assertThat(report.usage().get().outputTokens()).isEqualTo(45L);
        assertThat(report.usage().get().latencyMs()).isEqualTo(200L);
    }

    @Test
    void recommendation_hasFourValues() {
        assertThat(Recommendation.values()).containsExactlyInAnyOrder(
                Recommendation.APPLY_NOW, Recommendation.APPLY,
                Recommendation.CONSIDER, Recommendation.SKIP);
    }
}
