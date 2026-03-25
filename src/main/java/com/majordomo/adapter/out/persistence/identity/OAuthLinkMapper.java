package com.majordomo.adapter.out.persistence.identity;

import com.majordomo.domain.model.identity.OAuthLink;

final class OAuthLinkMapper {

    private OAuthLinkMapper() { }

    static OAuthLinkEntity toEntity(OAuthLink link) {
        var entity = new OAuthLinkEntity();
        entity.setId(link.getId());
        entity.setUserId(link.getUserId());
        entity.setProvider(link.getProvider());
        entity.setExternalId(link.getExternalId());
        entity.setEmail(link.getEmail());
        entity.setCreatedAt(link.getCreatedAt());
        entity.setUpdatedAt(link.getUpdatedAt());
        entity.setArchivedAt(link.getArchivedAt());
        return entity;
    }

    static OAuthLink toDomain(OAuthLinkEntity entity) {
        var link = new OAuthLink(entity.getId(), entity.getUserId(),
                entity.getProvider(), entity.getExternalId(), entity.getEmail());
        link.setCreatedAt(entity.getCreatedAt());
        link.setUpdatedAt(entity.getUpdatedAt());
        link.setArchivedAt(entity.getArchivedAt());
        return link;
    }
}
