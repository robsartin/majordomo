package com.majordomo.adapter.out.persistence.identity;

import com.majordomo.domain.model.identity.ApiKey;
import com.majordomo.domain.model.identity.ApiKeyScope;

final class ApiKeyMapper {

    private ApiKeyMapper() { }

    static ApiKeyEntity toEntity(ApiKey apiKey) {
        var entity = new ApiKeyEntity();
        entity.setId(apiKey.getId());
        entity.setOrganizationId(apiKey.getOrganizationId());
        entity.setName(apiKey.getName());
        entity.setHashedKey(apiKey.getHashedKey());
        entity.setScope(apiKey.getScope().name());
        entity.setLastUsedAt(apiKey.getLastUsedAt());
        entity.setCreatedAt(apiKey.getCreatedAt());
        entity.setUpdatedAt(apiKey.getUpdatedAt());
        entity.setExpiresAt(apiKey.getExpiresAt());
        entity.setArchivedAt(apiKey.getArchivedAt());
        return entity;
    }

    static ApiKey toDomain(ApiKeyEntity entity) {
        var apiKey = new ApiKey(entity.getId(), entity.getOrganizationId(),
                entity.getName(), entity.getHashedKey());
        // Legacy rows predating the scope column map to null; treat as READ_WRITE
        // to preserve prior behaviour (the migration also backfills READ_WRITE).
        apiKey.setScope(entity.getScope() == null
                ? ApiKeyScope.READ_WRITE : ApiKeyScope.valueOf(entity.getScope()));
        apiKey.setLastUsedAt(entity.getLastUsedAt());
        apiKey.setCreatedAt(entity.getCreatedAt());
        apiKey.setUpdatedAt(entity.getUpdatedAt());
        apiKey.setExpiresAt(entity.getExpiresAt());
        apiKey.setArchivedAt(entity.getArchivedAt());
        return apiKey;
    }
}
