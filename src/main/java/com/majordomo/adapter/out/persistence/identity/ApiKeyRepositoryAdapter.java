package com.majordomo.adapter.out.persistence.identity;

import com.majordomo.domain.model.identity.ApiKey;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence adapter that fulfills the {@link ApiKeyRepository}
 * output port by delegating to {@link JpaApiKeyRepository}.
 */
@Repository
public class ApiKeyRepositoryAdapter implements ApiKeyRepository {

    private final JpaApiKeyRepository jpa;

    /**
     * Constructs the adapter with the JPA repository.
     *
     * @param jpa the Spring Data JPA repository for API key entities
     */
    public ApiKeyRepositoryAdapter(JpaApiKeyRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public ApiKey save(ApiKey apiKey) {
        var entity = ApiKeyMapper.toEntity(apiKey);
        return ApiKeyMapper.toDomain(jpa.save(entity));
    }

    @Override
    public Optional<ApiKey> findById(UUID id) {
        return jpa.findById(id).map(ApiKeyMapper::toDomain);
    }

    @Override
    public Optional<ApiKey> findByHashedKey(String hashedKey) {
        return jpa.findByHashedKey(hashedKey).map(ApiKeyMapper::toDomain);
    }

    @Override
    public List<ApiKey> findByOrganizationId(UUID organizationId) {
        return jpa.findByOrganizationId(organizationId).stream()
                .map(ApiKeyMapper::toDomain)
                .toList();
    }
}
