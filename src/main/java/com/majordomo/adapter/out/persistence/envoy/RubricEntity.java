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
 * JPA entity for the {@code envoy_rubric} table. Source of truth for the rubric
 * is the JSONB {@code body} column; scalar columns exist for indexed lookup.
 *
 * <p>{@code organizationId} is nullable: {@code NULL} marks a system-default
 * template visible to every org; non-null rubrics are org-specific.</p>
 */
@Entity
@Table(name = "envoy_rubric")
public class RubricEntity {

    @Id
    private UUID id;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String body;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "created_by")
    private String createdBy;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public Instant getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(Instant effectiveFrom) { this.effectiveFrom = effectiveFrom; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
