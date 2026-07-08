package com.majordomo.application.herald;

import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.herald.CalendarToken;
import com.majordomo.domain.port.out.herald.CalendarTokenRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues, resolves, and revokes calendar-feed tokens. The raw token is a
 * high-entropy random value shown once at issue time and embedded in the feed
 * URL; only its SHA-256 hash is stored (fast O(1) lookup on each feed request,
 * same rationale as API keys).
 */
@Service
public class CalendarTokenService {

    private static final int TOKEN_RANDOM_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final CalendarTokenRepository repository;

    /**
     * Constructs the service.
     *
     * @param repository calendar token repository
     */
    public CalendarTokenService(CalendarTokenRepository repository) {
        this.repository = repository;
    }

    /**
     * Issues a new token for a user's organization, persisting only its hash.
     *
     * @param userId         the owning user
     * @param organizationId the organization the feed will publish
     * @return the raw token (shown once; not recoverable afterwards)
     */
    public String issue(UUID userId, UUID organizationId) {
        byte[] bytes = new byte[TOKEN_RANDOM_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        String raw = HexFormat.of().formatHex(bytes);
        CalendarToken token = new CalendarToken(
                UuidFactory.newId(), userId, organizationId, hash(raw));
        token.setCreatedAt(Instant.now());
        repository.save(token);
        return raw;
    }

    /**
     * Resolves a raw token to its active {@link CalendarToken}.
     *
     * @param rawToken the raw token from the feed URL
     * @return the active token, or empty if unknown or revoked
     */
    public Optional<CalendarToken> resolve(String rawToken) {
        return repository.findByHashedToken(hash(rawToken))
                .filter(CalendarToken::isActive);
    }

    /**
     * Lists a user's active tokens.
     *
     * @param userId the owning user
     * @return active tokens
     */
    public List<CalendarToken> listActive(UUID userId) {
        return repository.findActiveByUserId(userId);
    }

    /**
     * Revokes a token, but only if it belongs to the given user. Cross-user
     * revokes are silently ignored.
     *
     * @param tokenId the token id
     * @param userId  the requesting user (must own the token)
     */
    public void revoke(UUID tokenId, UUID userId) {
        repository.findById(tokenId)
                .filter(t -> userId.equals(t.getUserId()))
                .ifPresent(t -> {
                    t.setRevokedAt(Instant.now());
                    repository.save(t);
                });
    }

    /**
     * SHA-256 hex digest of a raw token. Public so tests and callers can compute
     * the stored form without duplicating the algorithm.
     *
     * @param raw the raw token
     * @return the lowercase hex SHA-256 digest
     */
    public static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
