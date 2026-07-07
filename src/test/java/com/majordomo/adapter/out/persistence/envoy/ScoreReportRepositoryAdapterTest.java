package com.majordomo.adapter.out.persistence.envoy;

import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.LlmScoreResponse;
import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.envoy.ScoreReport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.ComponentScan;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice test for {@link ScoreReportRepositoryAdapter} — round-trips a
 * {@link ScoreReport} through the JPA adapter (against the in-memory test
 * database) and asserts that LLM usage fields are preserved both when present
 * and when absent. The scalar {@code input_tokens} / {@code output_tokens} /
 * {@code latency_ms} columns are the canonical home for this data; this test
 * pins down their behaviour.
 */
@DataJpaTest
@AutoConfigureTestDatabase
@ComponentScan(basePackageClasses = ScoreReportRepositoryAdapter.class)
class ScoreReportRepositoryAdapterTest {

    @Autowired
    private ScoreReportRepositoryAdapter adapter;

    @Test
    void savesAndReloadsReportWithUsageFields() {
        UUID orgId = UuidFactory.newId();
        var usage = new LlmScoreResponse.Usage(1500L, 750L, 1234L);
        var report = new ScoreReport(
                UuidFactory.newId(),
                orgId,
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
                Instant.parse("2026-04-27T10:00:00Z"),
                Optional.of(usage));

        adapter.save(report);

        ScoreReport reloaded = adapter.findById(report.id(), orgId).orElseThrow();
        assertThat(reloaded.usage()).contains(usage);
        assertThat(reloaded.usage().get().inputTokens()).isEqualTo(1500L);
        assertThat(reloaded.usage().get().outputTokens()).isEqualTo(750L);
        assertThat(reloaded.usage().get().latencyMs()).isEqualTo(1234L);
    }

    @Test
    void savesAndReloadsReportWithoutUsage() {
        UUID orgId = UuidFactory.newId();
        var report = new ScoreReport(
                UuidFactory.newId(),
                orgId,
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
                Instant.parse("2026-04-27T10:00:00Z"));

        adapter.save(report);

        ScoreReport reloaded = adapter.findById(report.id(), orgId).orElseThrow();
        assertThat(reloaded.usage()).isEmpty();
    }

    @Test
    void contentHashRoundTripsThroughJsonBody() {
        UUID orgId = UuidFactory.newId();
        var report = reportBuilder(orgId, UuidFactory.newId(), UuidFactory.newId(),
                Instant.parse("2026-04-27T10:00:00Z"), "abc123hash");

        adapter.save(report);

        ScoreReport reloaded = adapter.findById(report.id(), orgId).orElseThrow();
        assertThat(reloaded.contentHash()).contains("abc123hash");
    }

    @Test
    void findLatestScoredReturnsMostRecentForPostingAndRubric() {
        UUID orgId = UuidFactory.newId();
        UUID postingId = UuidFactory.newId();
        UUID rubricId = UuidFactory.newId();
        adapter.save(reportBuilder(orgId, postingId, rubricId,
                Instant.parse("2026-04-27T10:00:00Z"), "old"));
        adapter.save(reportBuilder(orgId, postingId, rubricId,
                Instant.parse("2026-04-28T10:00:00Z"), "newest"));

        ScoreReport latest = adapter.findLatestScored(postingId, rubricId, orgId).orElseThrow();

        assertThat(latest.contentHash()).contains("newest");
    }

    @Test
    void findLatestScoredIsScopedToOrganization() {
        UUID postingId = UuidFactory.newId();
        UUID rubricId = UuidFactory.newId();
        UUID orgA = UuidFactory.newId();
        UUID orgB = UuidFactory.newId();
        adapter.save(reportBuilder(orgA, postingId, rubricId,
                Instant.parse("2026-04-27T10:00:00Z"), "org-a"));

        assertThat(adapter.findLatestScored(postingId, rubricId, orgB)).isEmpty();
    }

    @Test
    void findLatestScoredMissesWhenRubricVersionDiffers() {
        UUID orgId = UuidFactory.newId();
        UUID postingId = UuidFactory.newId();
        adapter.save(reportBuilder(orgId, postingId, UuidFactory.newId(),
                Instant.parse("2026-04-27T10:00:00Z"), "v1"));

        // A different rubric version is a different rubric row (id) → no match.
        assertThat(adapter.findLatestScored(postingId, UuidFactory.newId(), orgId)).isEmpty();
    }

    private static ScoreReport reportBuilder(
            UUID orgId, UUID postingId, UUID rubricId, Instant scoredAt, String contentHash) {
        return new ScoreReport(
                UuidFactory.newId(), orgId, postingId, rubricId, 1,
                Optional.empty(), List.of(), List.of(), 0, 0,
                Recommendation.SKIP, "claude-sonnet-4-6", scoredAt,
                Optional.empty(), Optional.of(contentHash));
    }
}
