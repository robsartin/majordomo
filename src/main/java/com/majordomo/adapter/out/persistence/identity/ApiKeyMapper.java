package com.majordomo.adapter.out.persistence.identity;

import com.majordomo.domain.model.identity.ApiKey;

final class ApiKeyMapper {

    private ApiKeyMapper() { }

    static ApiKeyEntity toEntity(ApiKey apiKey) {
        var entity = new ApiKeyEntity();
        entity.setId(apiKey.getId());
        entity.setOrganizationId(apiKey.getOrganizationId());
        entity.setName(apiKey.getName());
        entity.setHashedKey(apiKey.getHashedKey());
        entity.setCreatedAt(apiKey.getCreatedAt());
        entity.setUpdatedAt(apiKey.getUpdatedAt());
        entity.setExpiresAt(apiKey.getExpiresAt());
        entity.setArchivedAt(apiKey.getArchivedAt());
        return entity;
    }

    static ApiKey toDomain(ApiKeyEntity entity) {
        var apiKey = new ApiKey(entity.getId(), entity.getOrganizationId(),
                entity.getName(), entity.getHashedKey());
        apiKey.setCreatedAt(entity.getCreatedAt());
        apiKey.setUpdatedAt(entity.getUpdatedAt());
        apiKey.setExpiresAt(entity.getExpiresAt());
        apiKey.setArchivedAt(entity.getArchivedAt());
        return apiKey;
    }
}
