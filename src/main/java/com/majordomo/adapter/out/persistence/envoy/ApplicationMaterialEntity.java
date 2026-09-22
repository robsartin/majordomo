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
 * JPA entity for {@code envoy_application_material}. Source of truth is the
 * JSONB {@code body}; scalar columns exist for indexed query, as on
 * {@link ScoreReportEntity}.
 */
@Entity
@Table(name = "envoy_application_material")
public class ApplicationMaterialEntity {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "posting_id", nullable = false)
    private UUID postingId;

    @Column(name = "score_report_id")
    private UUID scoreReportId;

    @Column(nullable = false)
    private String kind;

    @Column(nullable = false)
    private String tone;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String body;

    @Column(name = "llm_model", nullable = false)
    private String llmModel;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

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

    public UUID getScoreReportId() { return scoreReportId; }
    public void setScoreReportId(UUID scoreReportId) { this.scoreReportId = scoreReportId; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public String getTone() { return tone; }
    public void setTone(String tone) { this.tone = tone; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getLlmModel() { return llmModel; }
    public void setLlmModel(String llmModel) { this.llmModel = llmModel; }

    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }

    public Long getInputTokens() { return inputTokens; }
    public void setInputTokens(Long inputTokens) { this.inputTokens = inputTokens; }

    public Long getOutputTokens() { return outputTokens; }
    public void setOutputTokens(Long outputTokens) { this.outputTokens = outputTokens; }

    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }
}
