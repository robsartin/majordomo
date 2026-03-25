package com.majordomo.adapter.out.persistence.identity;

import com.majordomo.domain.model.identity.Credential;

final class CredentialMapper {

    private CredentialMapper() {}

    static CredentialEntity toEntity(Credential credential) {
        var entity = new CredentialEntity();
        entity.setId(credential.getId());
        entity.setUserId(credential.getUserId());
        entity.setHashedPassword(credential.getHashedPassword());
        entity.setCreatedAt(credential.getCreatedAt());
        entity.setUpdatedAt(credential.getUpdatedAt());
        entity.setArchivedAt(credential.getArchivedAt());
        return entity;
    }

    static Credential toDomain(CredentialEntity entity) {
        var credential = new Credential(entity.getId(), entity.getUserId(), entity.getHashedPassword());
        credential.setCreatedAt(entity.getCreatedAt());
        credential.setUpdatedAt(entity.getUpdatedAt());
        credential.setArchivedAt(entity.getArchivedAt());
        return credential;
    }
}
