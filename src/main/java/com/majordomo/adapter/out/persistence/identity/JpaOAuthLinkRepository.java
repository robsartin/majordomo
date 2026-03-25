package com.majordomo.adapter.out.persistence.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link OAuthLinkEntity}, providing persistence operations
 * used by {@link OAuthLinkRepositoryAdapter}.
 */
public interface JpaOAuthLinkRepository extends JpaRepository<OAuthLinkEntity, UUID> {

    /**
     * Finds an OAuth link by provider and external identifier.
     *
     * @param provider   the OAuth provider name
     * @param externalId the provider-specific user identifier
     * @return the matching entity, or empty if none exists
     */
    Optional<OAuthLinkEntity> findByProviderAndExternalId(String provider, String externalId);

    /**
     * Returns all OAuth links belonging to a given user.
     *
     * @param userId the user ID
     * @return list of matching entities
     */
    List<OAuthLinkEntity> findByUserId(UUID userId);
}
