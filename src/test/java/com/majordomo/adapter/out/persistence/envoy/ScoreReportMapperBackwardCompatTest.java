package com.majordomo.adapter.out.persistence.envoy;

import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.Confidence;
import com.majordomo.domain.model.envoy.LlmScoreResponse;
import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.envoy.ScoreReport;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that older score-report JSON payloads, which do not include the
 * {@code confidence} field on each category score, still deserialise cleanly
 * through the same Jackson configuration used by {@link ScoreReportMapper}
 * and {@link RubricMapper}. The {@code body} JSONB column already holds rows
 * written before the confidence-per-category feature shipped — those rows
 * must continue to load.
 */
class ScoreReportMapperBackwardCompatTest {

    @Test
    void oldShapedScoreReportDeserialisesWithoutConfidenceField() {
        UUID id = UuidFactory.newId();
        UUID orgId = UuidFactory.newId();
        UUID postingId = UuidFactory.newId();
        UUID rubricId = UuidFactory.newId();
        Instant scoredAt = Instant.parse("2026-01-15T10:00:00Z");

        // Body shape predates issue #150 — no `confidence` key on any category.
        String legacyBody = """
                {
                  "id": "%s",
                  "organizationId": "%s",
                  "postingId": "%s",
                  "rubricId": "%s",
                  "rubricVersion": 1,
                  "disqualifiedBy": null,
                  "categoryScores": [
                    {"categoryKey": "compensation", "points": 15,
                     "tierLabel": "Good", "rationale": "salary listed"}
                  ],
                  "flagHits": [],
                  "rawScore": 15,
                  "finalScore": 15,
                  "recommendation": "APPLY",
                  "llmModel": "claude-sonnet-4-6",
                  "scoredAt": "2026-01-15T10:00:00Z"
                }
                """.formatted(id, orgId, postingId, rubricId);

        var entity = new ScoreReportEntity();
        entity.setId(id);
        entity.setOrganizationId(orgId);
        entity.setPostingId(postingId);
        entity.setRubricId(rubricId);
        entity.setRubricVersion(1);
        entity.setBody(legacyBody);
        entity.setFinalScore(15);
        entity.setRecommendation("APPLY");
        entity.setScoredAt(scoredAt);

        ScoreReport report = ScoreReportMapper.toDomain(entity);

        assertThat(report.id()).isEqualTo(id);
        assertThat(report.categoryScores()).hasSize(1);
        assertThat(report.categoryScores().get(0).categoryKey()).isEqualTo("compensation");
        assertThat(report.categoryScores().get(0).confidence()).isEmpty();
    }

    @Test
    void newShapedScoreReportRoundTripsThroughMapper() {
        UUID id = UuidFactory.newId();
        UUID orgId = UuidFactory.newId();
        UUID postingId = UuidFactory.newId();
        UUID rubricId = UuidFactory.newId();
        Instant scoredAt = Instant.parse("2026-04-23T10:00:00Z");

        String newBody = """
                {
                  "id": "%s",
                  "organizationId": "%s",
                  "postingId": "%s",
                  "rubricId": "%s",
                  "rubricVersion": 1,
                  "disqualifiedBy": null,
                  "categoryScores": [
                    {"categoryKey": "compensation", "points": 15,
                     "tierLabel": "Good", "rationale": "salary listed",
                     "confidence": "HIGH"},
                    {"categoryKey": "remote", "points": 5,
                     "tierLabel": "Hybrid", "rationale": "ambiguous",
                     "confidence": "LOW"}
                  ],
                  "flagHits": [],
                  "rawScore": 20,
                  "finalScore": 20,
                  "recommendation": "APPLY",
                  "llmModel": "claude-sonnet-4-6",
                  "scoredAt": "2026-04-23T10:00:00Z"
                }
                """.formatted(id, orgId, postingId, rubricId);

        var entity = new ScoreReportEntity();
        entity.setId(id);
        entity.setOrganizationId(orgId);
        entity.setPostingId(postingId);
        entity.setRubricId(rubricId);
        entity.setRubricVersion(1);
        entity.setBody(newBody);
        entity.setFinalScore(20);
        entity.setRecommendation("APPLY");
        entity.setScoredAt(scoredAt);

        ScoreReport report = ScoreReportMapper.toDomain(entity);

        assertThat(report.categoryScores()).hasSize(2);
        assertThat(report.categoryScores().get(0).confidence()).contains(Confidence.HIGH);
        assertThat(report.categoryScores().get(1).confidence()).contains(Confidence.LOW);

        // And round-trip back to JSON — the serialised form must still be
        // deserialisable by the same mapper.
        var roundTripEntity = ScoreReportMapper.toEntity(report);
        ScoreReport again = ScoreReportMapper.toDomain(roundTripEntity);
        assertThat(again.categoryScores().get(0).confidence()).contains(Confidence.HIGH);
        assertThat(again.categoryScores().get(1).confidence()).contains(Confidence.LOW);
    }

    @Test
    void usageRoundTripsThroughEntityScalarColumns() {
        var usage = new LlmScoreResponse.Usage(1500L, 750L, 1234L);
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
                Instant.parse("2026-04-27T10:00:00Z"),
                Optional.of(usage));

        var entity = ScoreReportMapper.toEntity(report);

        // Scalar columns are populated for indexed cost/latency queries.
        assertThat(entity.getInputTokens()).isEqualTo(1500L);
        assertThat(entity.getOutputTokens()).isEqualTo(750L);
        assertThat(entity.getLatencyMs()).isEqualTo(1234L);

        ScoreReport restored = ScoreReportMapper.toDomain(entity);
        assertThat(restored.usage()).contains(usage);
    }

    @Test
    void emptyUsageMapsToNullScalarColumns() {
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
                Instant.parse("2026-04-27T10:00:00Z"));

        var entity = ScoreReportMapper.toEntity(report);

        assertThat(entity.getInputTokens()).isNull();
        assertThat(entity.getOutputTokens()).isNull();
        assertThat(entity.getLatencyMs()).isNull();

        ScoreReport restored = ScoreReportMapper.toDomain(entity);
        assertThat(restored.usage()).isEmpty();
    }

    @Test
    void legacyRowsWithoutUsageColumnsLoadAsEmptyUsage() {
        // Simulates a row written before V16 migration: scalar columns are NULL,
        // and the JSONB body has no `usage` key.
        UUID id = UuidFactory.newId();
        UUID orgId = UuidFactory.newId();
        UUID postingId = UuidFactory.newId();
        UUID rubricId = UuidFactory.newId();
        Instant scoredAt = Instant.parse("2026-02-15T10:00:00Z");

        String legacyBody = """
                {
                  "id": "%s",
                  "organizationId": "%s",
                  "postingId": "%s",
                  "rubricId": "%s",
                  "rubricVersion": 1,
                  "disqualifiedBy": null,
                  "categoryScores": [],
                  "flagHits": [],
                  "rawScore": 0,
                  "finalScore": 0,
                  "recommendation": "SKIP",
                  "llmModel": "claude-sonnet-4-6",
                  "scoredAt": "2026-02-15T10:00:00Z"
                }
                """.formatted(id, orgId, postingId, rubricId);

        var entity = new ScoreReportEntity();
        entity.setId(id);
        entity.setOrganizationId(orgId);
        entity.setPostingId(postingId);
        entity.setRubricId(rubricId);
        entity.setRubricVersion(1);
        entity.setBody(legacyBody);
        entity.setFinalScore(0);
        entity.setRecommendation("SKIP");
        entity.setScoredAt(scoredAt);
        // Scalar usage columns left NULL, mirroring legacy rows.

        ScoreReport report = ScoreReportMapper.toDomain(entity);

        assertThat(report.usage()).isEmpty();
    }

    @Test
    void partiallyPopulatedUsageColumnsAreTreatedAsEmpty() {
        // Defensive: if some legacy/manual import filled only one or two of the
        // three columns, the mapper must not synthesise a half-formed Usage.
        UUID id = UuidFactory.newId();
        UUID orgId = UuidFactory.newId();
        UUID postingId = UuidFactory.newId();
        UUID rubricId = UuidFactory.newId();
        Instant scoredAt = Instant.parse("2026-03-01T10:00:00Z");

        String body = """
                {
                  "id": "%s",
                  "organizationId": "%s",
                  "postingId": "%s",
                  "rubricId": "%s",
                  "rubricVersion": 1,
                  "disqualifiedBy": null,
                  "categoryScores": [],
                  "flagHits": [],
                  "rawScore": 0,
                  "finalScore": 0,
                  "recommendation": "SKIP",
                  "llmModel": "claude-sonnet-4-6",
                  "scoredAt": "2026-03-01T10:00:00Z"
                }
                """.formatted(id, orgId, postingId, rubricId);

        var entity = new ScoreReportEntity();
        entity.setId(id);
        entity.setOrganizationId(orgId);
        entity.setPostingId(postingId);
        entity.setRubricId(rubricId);
        entity.setRubricVersion(1);
        entity.setBody(body);
        entity.setFinalScore(0);
        entity.setRecommendation("SKIP");
        entity.setScoredAt(scoredAt);
        entity.setInputTokens(100L);
        entity.setOutputTokens(null);
        entity.setLatencyMs(null);

        ScoreReport report = ScoreReportMapper.toDomain(entity);

        assertThat(report.usage()).isEmpty();
    }
}
