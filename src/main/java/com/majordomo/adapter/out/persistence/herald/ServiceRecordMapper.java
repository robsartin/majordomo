package com.majordomo.adapter.out.persistence.herald;

import com.majordomo.domain.model.herald.ServiceRecord;

final class ServiceRecordMapper {

    private ServiceRecordMapper() {}

    static ServiceRecordEntity toEntity(ServiceRecord record) {
        var entity = new ServiceRecordEntity();
        entity.setId(record.getId());
        entity.setPropertyId(record.getPropertyId());
        entity.setContactId(record.getContactId());
        entity.setScheduleId(record.getScheduleId());
        entity.setPerformedOn(record.getPerformedOn());
        entity.setDescription(record.getDescription());
        entity.setNotes(record.getNotes());
        entity.setCreatedAt(record.getCreatedAt());
        entity.setUpdatedAt(record.getUpdatedAt());
        entity.setArchivedAt(record.getArchivedAt());
        entity.setCost(record.getCost());
        return entity;
    }

    static ServiceRecord toDomain(ServiceRecordEntity entity) {
        var record = new ServiceRecord();
        record.setId(entity.getId());
        record.setPropertyId(entity.getPropertyId());
        record.setContactId(entity.getContactId());
        record.setScheduleId(entity.getScheduleId());
        record.setPerformedOn(entity.getPerformedOn());
        record.setDescription(entity.getDescription());
        record.setNotes(entity.getNotes());
        record.setCreatedAt(entity.getCreatedAt());
        record.setUpdatedAt(entity.getUpdatedAt());
        record.setArchivedAt(entity.getArchivedAt());
        record.setCost(entity.getCost());
        return record;
    }
}
