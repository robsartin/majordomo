package com.majordomo.adapter.out.persistence.envoy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code envoy_score_report} table. Source of truth is the
 * JSONB {@code body} column; scalar columns exist for indexed query.
 */
@Entity
@Table(name = "envoy_score_report")
public class ScoreReportEntity {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "posting_id", nullable = false)
    private UUID postingId;

    @Column(name = "rubric_id", nullable = false)
    private UUID rubricId;

    @Column(name = "rubric_version", nullable = false)
    private int rubricVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String body;

    @Column(name = "final_score", nullable = false)
    private int finalScore;

    @Column(nullable = false)
    private String recommendation;

    @Column(name = "scored_at", nullable = false)
    private Instant scoredAt;

    @Column(name = "input_tokens")
    private Long inputTokens;

    @Column(name = "output_tokens")
    private Long outputTokens;

    @Column(name = "latency_ms")
    private Long latencyMs;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }

    public UUID getPostingId() { return postingId; }
    public void setPostingId(UUID postingId) { this.postingId = postingId; }

    public UUID getRubricId() { return rubricId; }
    public void setRubricId(UUID rubricId) { this.rubricId = rubricId; }

    public int getRubricVersion() { return rubricVersion; }
    public void setRubricVersion(int rubricVersion) { this.rubricVersion = rubricVersion; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public int getFinalScore() { return finalScore; }
    public void setFinalScore(int finalScore) { this.finalScore = finalScore; }

    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }

    public Instant getScoredAt() { return scoredAt; }
    public void setScoredAt(Instant scoredAt) { this.scoredAt = scoredAt; }

    public Long getInputTokens() { return inputTokens; }
    public void setInputTokens(Long inputTokens) { this.inputTokens = inputTokens; }

    public Long getOutputTokens() { return outputTokens; }
    public void setOutputTokens(Long outputTokens) { this.outputTokens = outputTokens; }

    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }
}
