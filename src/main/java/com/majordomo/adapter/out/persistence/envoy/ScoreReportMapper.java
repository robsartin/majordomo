package com.majordomo.adapter.out.persistence.envoy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.majordomo.domain.model.envoy.ScoreReport;

/**
 * Maps between the {@link ScoreReport} record and {@link ScoreReportEntity},
 * serialising the record to/from JSON for the {@code body} JSONB column.
 */
final class ScoreReportMapper {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .addModule(new Jdk8Module())
            .build();

    private ScoreReportMapper() { }

    static ScoreReportEntity toEntity(ScoreReport report) {
        var e = new ScoreReportEntity();
        e.setId(report.id());
        e.setOrganizationId(report.organizationId());
        e.setPostingId(report.postingId());
        e.setRubricId(report.rubricId());
        e.setRubricVersion(report.rubricVersion());
        e.setFinalScore(report.finalScore());
        e.setRecommendation(report.recommendation().name());
        e.setScoredAt(report.scoredAt());
        try {
            e.setBody(MAPPER.writeValueAsString(report));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialise ScoreReport", ex);
        }
        return e;
    }

    static ScoreReport toDomain(ScoreReportEntity e) {
        try {
            return MAPPER.readValue(e.getBody(), ScoreReport.class);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Failed to deserialise ScoreReport " + e.getId(), ex);
        }
    }
}
