package com.majordomo.adapter.out.persistence.identity;

import com.majordomo.domain.model.identity.OAuthLink;
import com.majordomo.domain.port.out.identity.OAuthLinkRepository;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence adapter that fulfills the {@link OAuthLinkRepository}
 * output port by delegating to {@link JpaOAuthLinkRepository}.
 */
@Repository
public class OAuthLinkRepositoryAdapter implements OAuthLinkRepository {

    private final JpaOAuthLinkRepository jpa;

    /**
     * Constructs the adapter with the JPA repository.
     *
     * @param jpa the Spring Data JPA repository for OAuth links
     */
    public OAuthLinkRepositoryAdapter(JpaOAuthLinkRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public OAuthLink save(OAuthLink link) {
        var entity = OAuthLinkMapper.toEntity(link);
        return OAuthLinkMapper.toDomain(jpa.save(entity));
    }

    @Override
    public Optional<OAuthLink> findByProviderAndExternalId(String provider, String externalId) {
        return jpa.findByProviderAndExternalId(provider, externalId)
                .map(OAuthLinkMapper::toDomain);
    }

    @Override
    public List<OAuthLink> findByUserId(UUID userId) {
        return jpa.findByUserId(userId).stream()
                .map(OAuthLinkMapper::toDomain)
                .toList();
    }
}
