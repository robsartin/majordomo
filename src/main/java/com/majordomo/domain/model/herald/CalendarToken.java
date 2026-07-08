package com.majordomo.domain.model.herald;

import java.time.Instant;
import java.util.UUID;

/**
 * A per-user secret that authenticates an unauthenticated iCalendar feed request
 * (the token travels in the feed URL, not the session). Stored hashed; only the
 * SHA-256 hash is persisted. Soft-revoked by setting {@code revokedAt}.
 */
public class CalendarToken {

    private UUID id;
    private UUID userId;
    private UUID organizationId;
    private String hashedToken;
    private Instant createdAt;
    private Instant revokedAt;

    public CalendarToken() { }

    public CalendarToken(UUID id, UUID userId, UUID organizationId, String hashedToken) {
        this.id = id;
        this.userId = userId;
        this.organizationId = organizationId;
        this.hashedToken = hashedToken;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }

    public String getHashedToken() { return hashedToken; }
    public void setHashedToken(String hashedToken) { this.hashedToken = hashedToken; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }

    /**
     * Whether this token is still usable (not revoked).
     *
     * @return {@code true} if {@code revokedAt} is unset
     */
    public boolean isActive() {
        return revokedAt == null;
    }
}
