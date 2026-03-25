package com.majordomo.adapter.out.persistence.identity;

import com.majordomo.domain.model.identity.Organization;

final class OrganizationMapper {

    private OrganizationMapper() {}

    static OrganizationEntity toEntity(Organization org) {
        var entity = new OrganizationEntity();
        entity.setId(org.getId());
        entity.setName(org.getName());
        entity.setCreatedAt(org.getCreatedAt());
        entity.setUpdatedAt(org.getUpdatedAt());
        entity.setArchivedAt(org.getArchivedAt());
        return entity;
    }

    static Organization toDomain(OrganizationEntity entity) {
        var org = new Organization(entity.getId(), entity.getName());
        org.setCreatedAt(entity.getCreatedAt());
        org.setUpdatedAt(entity.getUpdatedAt());
        org.setArchivedAt(entity.getArchivedAt());
        return org;
    }
}
