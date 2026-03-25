package com.majordomo.domain.port.out.identity;

import com.majordomo.domain.model.identity.Credential;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for persisting and retrieving user credentials.
 * Credentials hold authentication secrets (e.g. hashed passwords) and are
 * always scoped to a single user.
 */
public interface CredentialRepository {

    /**
     * Persists a credential, inserting or updating as needed.
     *
     * @param credential the credential to save
     * @return the saved credential, including any generated or updated fields
     */
    Credential save(Credential credential);

    /**
     * Retrieves the credential associated with the given user.
     *
     * @param userId the ID of the user whose credential is sought
     * @return the credential, or empty if none exists for that user
     */
    Optional<Credential> findByUserId(UUID userId);
}
