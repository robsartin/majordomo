package com.majordomo.adapter.out.persistence.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ApiKeyEntity}, providing persistence operations
 * used by {@link ApiKeyRepositoryAdapter}.
 */
public interface JpaApiKeyRepository extends JpaRepository<ApiKeyEntity, UUID> {

    /**
     * Finds an API key entity by its SHA-256 hashed key value.
     *
     * @param hashedKey the SHA-256 hex digest
     * @return the matching entity, or empty if not found
     */
    Optional<ApiKeyEntity> findByHashedKey(String hashedKey);

    /**
     * Lists all API key entities belonging to the specified organization.
     *
     * @param organizationId the UUID of the organization
     * @return a list of matching entities
     */
    List<ApiKeyEntity> findByOrganizationId(UUID organizationId);

    /**
     * Updates only the {@code lastUsedAt} column for a key. A targeted update so
     * recording usage on every authenticated request does not load or rewrite the
     * whole row.
     *
     * @param id         the API key id
     * @param lastUsedAt the timestamp to store
     * @return the number of rows updated (0 or 1)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE ApiKeyEntity a SET a.lastUsedAt = :lastUsedAt WHERE a.id = :id")
    int touchLastUsed(@Param("id") UUID id, @Param("lastUsedAt") Instant lastUsedAt);
}
