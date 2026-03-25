package com.majordomo.adapter.in.web.identity;

import com.majordomo.adapter.in.web.config.ApiKeyAuthenticationFilter;
import com.majordomo.domain.model.identity.ApiKey;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;

import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for managing API keys within organizations.
 *
 * <p>Provides endpoints to create, list, and revoke API keys. The plaintext key
 * is returned exactly once at creation time; after that only metadata is available.</p>
 */
@RestController
@RequestMapping("/api/organizations/{orgId}/api-keys")
@Tag(name = "Identity", description = "API key management")
public class ApiKeyController {

    private static final String KEY_PREFIX = "mjd_";
    private static final int KEY_RANDOM_BYTES = 32;

    private final ApiKeyRepository apiKeyRepository;

    /**
     * Constructs the controller with the API key repository.
     *
     * @param apiKeyRepository the outbound port for API key persistence
     */
    public ApiKeyController(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    /**
     * Creates a new API key for the specified organization. The plaintext key
     * is returned in the response and cannot be retrieved again.
     *
     * @param orgId   the organization UUID
     * @param request the request body containing the key name and optional expiration
     * @return {@code 201 Created} with the key metadata and plaintext key
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @PathVariable UUID orgId,
            @RequestBody CreateApiKeyRequest request) {
        String rawKey = generateRawKey();
        String hashedKey = ApiKeyAuthenticationFilter.sha256(rawKey);

        var now = Instant.now();
        var apiKey = new ApiKey(UUID.randomUUID(), orgId, request.name(), hashedKey);
        apiKey.setCreatedAt(now);
        apiKey.setUpdatedAt(now);
        apiKey.setExpiresAt(request.expiresAt());

        var saved = apiKeyRepository.save(apiKey);

        Map<String, Object> body = Map.of(
                "id", saved.getId(),
                "organizationId", saved.getOrganizationId(),
                "name", saved.getName(),
                "key", rawKey,
                "createdAt", saved.getCreatedAt(),
                "expiresAt", saved.getExpiresAt() != null ? saved.getExpiresAt() : ""
        );

        return ResponseEntity
                .created(URI.create("/api/organizations/" + orgId
                        + "/api-keys/" + saved.getId()))
                .body(body);
    }

    /**
     * Lists all API keys for the specified organization, excluding archived keys.
     * Plaintext keys are never returned.
     *
     * @param orgId the organization UUID
     * @return a list of API key metadata records
     */
    @GetMapping
    public List<ApiKeyResponse> list(@PathVariable UUID orgId) {
        return apiKeyRepository.findByOrganizationId(orgId).stream()
                .filter(k -> k.getArchivedAt() == null)
                .map(k -> new ApiKeyResponse(
                        k.getId(), k.getName(), k.getCreatedAt(), k.getExpiresAt()))
                .toList();
    }

    /**
     * Revokes (soft-deletes) an API key by setting its archived_at timestamp.
     *
     * @param orgId the organization UUID
     * @param id    the UUID of the API key to revoke
     * @return {@code 204 No Content} on success, or {@code 404 Not Found}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(@PathVariable UUID orgId, @PathVariable UUID id) {
        var apiKey = apiKeyRepository.findById(id)
                .filter(k -> k.getOrganizationId().equals(orgId));

        if (apiKey.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        var key = apiKey.get();
        key.setArchivedAt(Instant.now());
        key.setUpdatedAt(Instant.now());
        apiKeyRepository.save(key);

        return ResponseEntity.noContent().build();
    }

    private String generateRawKey() {
        var random = new SecureRandom();
        byte[] bytes = new byte[KEY_RANDOM_BYTES];
        random.nextBytes(bytes);
        return KEY_PREFIX + HexFormat.of().formatHex(bytes);
    }

    /**
     * Request body for creating a new API key.
     *
     * @param name      display name for the key
     * @param expiresAt optional expiration instant
     */
    public record CreateApiKeyRequest(String name, Instant expiresAt) { }

    /**
     * Response record for listing API keys (no plaintext key included).
     *
     * @param id        the key ID
     * @param name      display name
     * @param createdAt creation timestamp
     * @param expiresAt optional expiration timestamp
     */
    public record ApiKeyResponse(UUID id, String name, Instant createdAt, Instant expiresAt) { }
}
