package com.majordomo.adapter.out.persistence.herald;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link CalendarTokenEntity}. */
public interface JpaCalendarTokenRepository extends JpaRepository<CalendarTokenEntity, UUID> {

    /**
     * Finds a token by its SHA-256 hash.
     *
     * @param hashedToken the SHA-256 hex digest
     * @return the matching entity, or empty
     */
    Optional<CalendarTokenEntity> findByHashedToken(String hashedToken);

    /**
     * Lists a user's non-revoked tokens.
     *
     * @param userId the owning user
     * @return active token entities
     */
    List<CalendarTokenEntity> findByUserIdAndRevokedAtIsNull(UUID userId);
}
