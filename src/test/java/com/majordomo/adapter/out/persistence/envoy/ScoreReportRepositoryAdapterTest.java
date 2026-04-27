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
}
