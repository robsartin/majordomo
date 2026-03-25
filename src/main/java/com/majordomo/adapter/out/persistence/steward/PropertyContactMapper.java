package com.majordomo.adapter.out.persistence.steward;

import com.majordomo.domain.model.steward.PropertyContact;

final class PropertyContactMapper {

    private PropertyContactMapper() {}

    static PropertyContactEntity toEntity(PropertyContact pc) {
        var entity = new PropertyContactEntity();
        entity.setId(pc.getId());
        entity.setPropertyId(pc.getPropertyId());
        entity.setContactId(pc.getContactId());
        entity.setRole(pc.getRole());
        entity.setNotes(pc.getNotes());
        entity.setCreatedAt(pc.getCreatedAt());
        entity.setUpdatedAt(pc.getUpdatedAt());
        entity.setArchivedAt(pc.getArchivedAt());
        return entity;
    }

    static PropertyContact toDomain(PropertyContactEntity entity) {
        var pc = new PropertyContact();
        pc.setId(entity.getId());
        pc.setPropertyId(entity.getPropertyId());
        pc.setContactId(entity.getContactId());
        pc.setRole(entity.getRole());
        pc.setNotes(entity.getNotes());
        pc.setCreatedAt(entity.getCreatedAt());
        pc.setUpdatedAt(entity.getUpdatedAt());
        pc.setArchivedAt(entity.getArchivedAt());
        return pc;
    }
}
