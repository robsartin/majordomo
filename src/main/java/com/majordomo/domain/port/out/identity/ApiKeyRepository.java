package com.majordomo.domain.port.out.identity;

import com.majordomo.domain.model.identity.ApiKey;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for API key persistence.
 * API keys are scoped to organizations and enable machine-to-machine authentication.
 */
public interface ApiKeyRepository {

    /**
     * Persists an API key, inserting or updating as needed.
     *
     * @param apiKey the API key to save
     * @return the saved API key, including any generated or updated fields
     */
    ApiKey save(ApiKey apiKey);

    /**
     * Retrieves an API key by its unique identifier.
     *
     * @param id the UUID of the API key
     * @return the API key, or empty if none exists with that ID
     */
    Optional<ApiKey> findById(UUID id);

    /**
     * Finds an API key by its SHA-256 hashed key value.
     *
     * @param hashedKey the SHA-256 hex digest of the raw API key
     * @return the matching API key, or empty if no match is found
     */
    Optional<ApiKey> findByHashedKey(String hashedKey);

    /**
     * Lists all API keys belonging to the specified organization.
     *
     * @param organizationId the UUID of the organization
     * @return a list of API keys for that organization (may be empty)
     */
    List<ApiKey> findByOrganizationId(UUID organizationId);
}
