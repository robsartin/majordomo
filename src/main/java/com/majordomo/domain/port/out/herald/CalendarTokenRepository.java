package com.majordomo.domain.port.out.herald;

import com.majordomo.domain.model.herald.CalendarToken;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for calendar-feed token persistence. Tokens authenticate the
 * public iCalendar feed endpoint and are stored hashed.
 */
public interface CalendarTokenRepository {

    /**
     * Persists a calendar token, inserting or updating as needed.
     *
     * @param token the token to save
     * @return the saved token
     */
    CalendarToken save(CalendarToken token);

    /**
     * Retrieves a token by id.
     *
     * @param id the token id
     * @return the token, or empty if not found
     */
    Optional<CalendarToken> findById(UUID id);

    /**
     * Finds a token by its SHA-256 hash.
     *
     * @param hashedToken the SHA-256 hex digest of the raw token
     * @return the matching token, or empty if none exists
     */
    Optional<CalendarToken> findByHashedToken(String hashedToken);

    /**
     * Lists a user's non-revoked tokens.
     *
     * @param userId the owning user
     * @return active tokens for that user (may be empty)
     */
    List<CalendarToken> findActiveByUserId(UUID userId);
}
