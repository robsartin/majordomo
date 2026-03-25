package com.majordomo.domain.model.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * Links an external OAuth2 identity to a Majordomo user.
 * Provider-agnostic design supports Google, GitHub, etc.
 */
public class OAuthLink {

    private UUID id;
    private UUID userId;
    private String provider;
    private String externalId;
    private String email;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant archivedAt;

    public OAuthLink() { }

    public OAuthLink(UUID id, UUID userId, String provider, String externalId, String email) {
        this.id = id;
        this.userId = userId;
        this.provider = provider;
        this.externalId = externalId;
        this.email = email;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getArchivedAt() { return archivedAt; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }
}
