package com.majordomo.domain.model.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * Stores the hashed authentication credential for a {@link User}.
 * A credential is scoped to a single user and may be archived when superseded.
 */
public class Credential {

    private UUID id;
    private UUID userId;
    private String hashedPassword;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant archivedAt;

    public Credential() {}

    public Credential(UUID id, UUID userId, String hashedPassword) {
        this.id = id;
        this.userId = userId;
        this.hashedPassword = hashedPassword;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getHashedPassword() { return hashedPassword; }
    public void setHashedPassword(String hashedPassword) { this.hashedPassword = hashedPassword; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getArchivedAt() { return archivedAt; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }
}
