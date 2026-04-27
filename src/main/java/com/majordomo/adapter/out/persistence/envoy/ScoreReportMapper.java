package com.majordomo.adapter.out.persistence.envoy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.majordomo.domain.model.envoy.LlmScoreResponse;
import com.majordomo.domain.model.envoy.ScoreReport;

import java.util.Optional;

/**
 * Maps between the {@link ScoreReport} record and {@link ScoreReportEntity},
 * serialising the record to/from JSON for the {@code body} JSONB column.
 *
 * <p>LLM usage data ({@code inputTokens}, {@code outputTokens}, {@code latencyMs}) is
 * mirrored into dedicated nullable scalar columns on the entity so cost/latency reports
 * can be aggregated without parsing JSONB. The JSONB body remains the source of truth;
 * the scalar columns are derived state. On read we synthesise a {@link
 * LlmScoreResponse.Usage} only when all three columns are present — partially populated
 * rows (which should not occur in practice but could from manual imports or legacy data)
 * are treated as if usage were absent rather than producing a half-formed value.
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
        report.usage().ifPresentOrElse(
                u -> {
                    e.setInputTokens(u.inputTokens());
                    e.setOutputTokens(u.outputTokens());
                    e.setLatencyMs(u.latencyMs());
                },
                () -> {
                    e.setInputTokens(null);
                    e.setOutputTokens(null);
                    e.setLatencyMs(null);
                });
        try {
            e.setBody(MAPPER.writeValueAsString(report));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialise ScoreReport", ex);
        }
        return e;
    }

    static ScoreReport toDomain(ScoreReportEntity e) {
        ScoreReport fromBody;
        try {
            fromBody = MAPPER.readValue(e.getBody(), ScoreReport.class);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Failed to deserialise ScoreReport " + e.getId(), ex);
        }
        Optional<LlmScoreResponse.Usage> columnUsage = readUsageFromColumns(e);
        // Prefer the scalar columns when present. They are the canonical home for usage
        // data going forward; the JSONB body may also carry a `usage` field (newly
        // written reports) but legacy bodies will not.
        if (columnUsage.isPresent()) {
            return new ScoreReport(
                    fromBody.id(),
                    fromBody.organizationId(),
                    fromBody.postingId(),
                    fromBody.rubricId(),
                    fromBody.rubricVersion(),
                    fromBody.disqualifiedBy(),
                    fromBody.categoryScores(),
                    fromBody.flagHits(),
                    fromBody.rawScore(),
                    fromBody.finalScore(),
                    fromBody.recommendation(),
                    fromBody.llmModel(),
                    fromBody.scoredAt(),
                    columnUsage);
        }
        return fromBody;
    }

    private static Optional<LlmScoreResponse.Usage> readUsageFromColumns(ScoreReportEntity e) {
        Long input = e.getInputTokens();
        Long output = e.getOutputTokens();
        Long latency = e.getLatencyMs();
        if (input == null || output == null || latency == null) {
            return Optional.empty();
        }
        return Optional.of(new LlmScoreResponse.Usage(input, output, latency));
    }
}
