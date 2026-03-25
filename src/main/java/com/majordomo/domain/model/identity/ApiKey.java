package com.majordomo.domain.model.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * An API key scoped to an organization, used for machine-to-machine authentication.
 * The key itself is hashed at rest; only the prefix is stored for identification.
 */
public class ApiKey {

    private UUID id;
    private UUID organizationId;
    private String name;
    private String hashedKey;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant expiresAt;
    private Instant archivedAt;

    public ApiKey() { }

    public ApiKey(UUID id, UUID organizationId, String name, String hashedKey) {
        this.id = id;
        this.organizationId = organizationId;
        this.name = name;
        this.hashedKey = hashedKey;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getHashedKey() { return hashedKey; }
    public void setHashedKey(String hashedKey) { this.hashedKey = hashedKey; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getArchivedAt() { return archivedAt; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }
}
