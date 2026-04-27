package com.majordomo.adapter.out.persistence.envoy;

import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.Confidence;
import com.majordomo.domain.model.envoy.ScoreReport;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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
}
