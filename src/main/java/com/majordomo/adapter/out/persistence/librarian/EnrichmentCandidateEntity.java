package com.majordomo.adapter.out.persistence.librarian;

import com.majordomo.domain.model.librarian.Confidence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "book_enrichment_candidates")
public class EnrichmentCandidateEntity {

    @Id
    private UUID id;

    @Column(name = "book_id", nullable = false)
    private UUID bookId;

    @Column(nullable = false)
    private String source;

    @Column(name = "external_id")
    private String externalId;

    @Column(nullable = false)
    private double score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Confidence confidence;

    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    @Column(name = "retrieved_at", nullable = false)
    private Instant retrievedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    private Boolean accepted;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getBookId() { return bookId; }
    public void setBookId(UUID bookId) { this.bookId = bookId; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }

    public Confidence getConfidence() { return confidence; }
    public void setConfidence(Confidence confidence) { this.confidence = confidence; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public Instant getRetrievedAt() { return retrievedAt; }
    public void setRetrievedAt(Instant retrievedAt) { this.retrievedAt = retrievedAt; }

    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }

    public Boolean getAccepted() { return accepted; }
    public void setAccepted(Boolean accepted) { this.accepted = accepted; }
}
