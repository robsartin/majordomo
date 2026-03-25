package com.majordomo.domain.model.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents the association between a {@link User} and an {@link Organization},
 * capturing the user's assigned {@link MemberRole} within that organization.
 */
public class Membership {

    private UUID id;
    private UUID userId;
    private UUID organizationId;
    private MemberRole role;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant archivedAt;

    public Membership() {}

    public Membership(UUID id, UUID userId, UUID organizationId, MemberRole role) {
        this.id = id;
        this.userId = userId;
        this.organizationId = organizationId;
        this.role = role;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }

    public MemberRole getRole() { return role; }
    public void setRole(MemberRole role) { this.role = role; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getArchivedAt() { return archivedAt; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }
}
