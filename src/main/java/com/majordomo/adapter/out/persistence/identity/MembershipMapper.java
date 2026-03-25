package com.majordomo.adapter.out.persistence.identity;

import com.majordomo.domain.model.identity.Membership;

final class MembershipMapper {

    private MembershipMapper() {}

    static MembershipEntity toEntity(Membership membership) {
        var entity = new MembershipEntity();
        entity.setId(membership.getId());
        entity.setUserId(membership.getUserId());
        entity.setOrganizationId(membership.getOrganizationId());
        entity.setRole(membership.getRole());
        entity.setCreatedAt(membership.getCreatedAt());
        entity.setUpdatedAt(membership.getUpdatedAt());
        entity.setArchivedAt(membership.getArchivedAt());
        return entity;
    }

    static Membership toDomain(MembershipEntity entity) {
        var membership = new Membership(entity.getId(), entity.getUserId(), entity.getOrganizationId(), entity.getRole());
        membership.setCreatedAt(entity.getCreatedAt());
        membership.setUpdatedAt(entity.getUpdatedAt());
        membership.setArchivedAt(entity.getArchivedAt());
        return membership;
    }
}
