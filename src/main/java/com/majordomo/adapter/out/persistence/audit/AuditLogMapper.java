package com.majordomo.adapter.out.persistence.audit;

import com.majordomo.domain.model.AuditLogEntry;

final class AuditLogMapper {

    private AuditLogMapper() { }

    static AuditLogEntity toEntity(AuditLogEntry entry) {
        var entity = new AuditLogEntity();
        entity.setId(entry.getId());
        entity.setEntityType(entry.getEntityType());
        entity.setEntityId(entry.getEntityId());
        entity.setAction(entry.getAction());
        entity.setUserId(entry.getUserId());
        entity.setOccurredAt(entry.getOccurredAt());
        entity.setDiffJson(entry.getDiffJson());
        return entity;
    }

    static AuditLogEntry toDomain(AuditLogEntity entity) {
        var entry = new AuditLogEntry();
        entry.setId(entity.getId());
        entry.setEntityType(entity.getEntityType());
        entry.setEntityId(entity.getEntityId());
        entry.setAction(entity.getAction());
        entry.setUserId(entity.getUserId());
        entry.setOccurredAt(entity.getOccurredAt());
        entry.setDiffJson(entity.getDiffJson());
        return entry;
    }
}
