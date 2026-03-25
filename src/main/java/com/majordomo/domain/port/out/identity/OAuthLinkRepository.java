package com.majordomo.domain.port.out.identity;

import com.majordomo.domain.model.identity.OAuthLink;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for persisting and querying OAuth2 identity links.
 * Each link associates an external provider identity (e.g. Google {@code sub} claim)
 * with a Majordomo {@link com.majordomo.domain.model.identity.User}.
 */
public interface OAuthLinkRepository {

    /**
     * Persists an OAuth link, inserting or updating as needed.
     *
     * @param link the OAuth link to save
     * @return the saved link, including any generated or updated fields
     */
    OAuthLink save(OAuthLink link);

    /**
     * Finds an OAuth link by its provider and external identifier.
     *
     * @param provider   the OAuth provider name (e.g. "google")
     * @param externalId the provider-specific user identifier
     * @return the matching link, or empty if none exists
     */
    Optional<OAuthLink> findByProviderAndExternalId(String provider, String externalId);

    /**
     * Returns all OAuth links belonging to a given user.
     *
     * @param userId the user whose OAuth links are sought
     * @return list of OAuth links for that user, or an empty list if none exist
     */
    List<OAuthLink> findByUserId(UUID userId);
}
