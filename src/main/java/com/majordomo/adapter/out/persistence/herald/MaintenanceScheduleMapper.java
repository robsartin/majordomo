package com.majordomo.adapter.out.persistence.herald;

import com.majordomo.domain.model.herald.MaintenanceSchedule;

final class MaintenanceScheduleMapper {

    private MaintenanceScheduleMapper() {}

    static MaintenanceScheduleEntity toEntity(MaintenanceSchedule schedule) {
        var entity = new MaintenanceScheduleEntity();
        entity.setId(schedule.getId());
        entity.setPropertyId(schedule.getPropertyId());
        entity.setContactId(schedule.getContactId());
        entity.setDescription(schedule.getDescription());
        entity.setFrequency(schedule.getFrequency());
        entity.setCustomIntervalDays(schedule.getCustomIntervalDays());
        entity.setNextDue(schedule.getNextDue());
        entity.setCreatedAt(schedule.getCreatedAt());
        entity.setUpdatedAt(schedule.getUpdatedAt());
        entity.setArchivedAt(schedule.getArchivedAt());
        entity.setNotificationSentAt(schedule.getNotificationSentAt());
        return entity;
    }

    static MaintenanceSchedule toDomain(MaintenanceScheduleEntity entity) {
        var schedule = new MaintenanceSchedule();
        schedule.setId(entity.getId());
        schedule.setPropertyId(entity.getPropertyId());
        schedule.setContactId(entity.getContactId());
        schedule.setDescription(entity.getDescription());
        schedule.setFrequency(entity.getFrequency());
        schedule.setCustomIntervalDays(entity.getCustomIntervalDays());
        schedule.setNextDue(entity.getNextDue());
        schedule.setCreatedAt(entity.getCreatedAt());
        schedule.setUpdatedAt(entity.getUpdatedAt());
        schedule.setArchivedAt(entity.getArchivedAt());
        schedule.setNotificationSentAt(entity.getNotificationSentAt());
        return schedule;
    }
}
