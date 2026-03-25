package com.majordomo.adapter.out.persistence.identity;

import com.majordomo.domain.model.identity.Credential;
import com.majordomo.domain.port.out.identity.CredentialRepository;

import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence adapter that fulfills the {@link com.majordomo.domain.port.out.identity.CredentialRepository}
 * output port by delegating to {@link JpaCredentialRepository}.
 */
@Repository
public class CredentialRepositoryAdapter implements CredentialRepository {

    private final JpaCredentialRepository jpa;

    public CredentialRepositoryAdapter(JpaCredentialRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Credential save(Credential credential) {
        var entity = CredentialMapper.toEntity(credential);
        return CredentialMapper.toDomain(jpa.save(entity));
    }

    @Override
    public Optional<Credential> findByUserId(UUID userId) {
        return jpa.findByUserId(userId).map(CredentialMapper::toDomain);
    }
}
