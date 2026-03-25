package com.majordomo.application.herald;

import com.majordomo.domain.model.EntityNotFoundException;
import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.herald.MaintenanceSchedule;
import com.majordomo.domain.model.herald.ServiceRecord;
import com.majordomo.domain.port.in.herald.ManageScheduleUseCase;
import com.majordomo.domain.port.out.herald.MaintenanceScheduleRepository;
import com.majordomo.domain.port.out.herald.ServiceRecordRepository;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service implementing schedule management use cases.
 * Bridges inbound ports to outbound repository ports for maintenance schedules
 * and service records.
 */
@Service
public class ScheduleService implements ManageScheduleUseCase {

    private final MaintenanceScheduleRepository scheduleRepository;
    private final ServiceRecordRepository serviceRecordRepository;

    /**
     * Constructs the service with the required repository ports.
     *
     * @param scheduleRepository      the outbound port for schedule persistence
     * @param serviceRecordRepository the outbound port for service record persistence
     */
    public ScheduleService(
            MaintenanceScheduleRepository scheduleRepository,
            ServiceRecordRepository serviceRecordRepository) {
        this.scheduleRepository = scheduleRepository;
        this.serviceRecordRepository = serviceRecordRepository;
    }

    @Override
    public MaintenanceSchedule create(MaintenanceSchedule schedule) {
        schedule.setId(UUID.randomUUID());
        schedule.setCreatedAt(Instant.now());
        schedule.setUpdatedAt(Instant.now());
        return scheduleRepository.save(schedule);
    }

    @Override
    public Optional<MaintenanceSchedule> findById(UUID id) {
        return scheduleRepository.findById(id);
    }

    @Override
    public List<MaintenanceSchedule> findByPropertyId(UUID propertyId) {
        return scheduleRepository.findByPropertyId(propertyId);
    }

    @Override
    public Page<MaintenanceSchedule> findByPropertyId(UUID propertyId, UUID cursor, int limit) {
        int clampedLimit = Math.max(1, Math.min(limit, 100));
        var items = scheduleRepository.findByPropertyId(propertyId, cursor, clampedLimit + 1);
        boolean hasMore = items.size() > clampedLimit;
        if (hasMore) {
            items = items.subList(0, clampedLimit);
        }
        UUID nextCursor = hasMore ? items.get(items.size() - 1).getId() : null;
        return new Page<>(items, nextCursor, hasMore);
    }

    @Override
    public List<MaintenanceSchedule> findDueBefore(LocalDate date) {
        return scheduleRepository.findDueBefore(date);
    }

    @Override
    public ServiceRecord recordService(UUID scheduleId, ServiceRecord record) {
        record.setId(UUID.randomUUID());
        record.setScheduleId(scheduleId);
        record.setCreatedAt(Instant.now());
        record.setUpdatedAt(Instant.now());
        return serviceRecordRepository.save(record);
    }

    @Override
    public List<ServiceRecord> findRecordsByScheduleId(UUID scheduleId) {
        return serviceRecordRepository.findByScheduleId(scheduleId);
    }

    @Override
    public MaintenanceSchedule update(UUID id, MaintenanceSchedule schedule) {
        var existing = scheduleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("MaintenanceSchedule", id));
        schedule.setId(existing.getId());
        schedule.setCreatedAt(existing.getCreatedAt());
        schedule.setUpdatedAt(Instant.now());
        return scheduleRepository.save(schedule);
    }

    @Override
    public void archive(UUID id) {
        var existing = scheduleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("MaintenanceSchedule", id));
        existing.setArchivedAt(Instant.now());
        scheduleRepository.save(existing);
    }

    @Override
    public ServiceRecord updateRecord(UUID id, ServiceRecord record) {
        var existing = serviceRecordRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("ServiceRecord", id));
        record.setId(existing.getId());
        record.setCreatedAt(existing.getCreatedAt());
        record.setUpdatedAt(Instant.now());
        return serviceRecordRepository.save(record);
    }

    @Override
    public void archiveRecord(UUID id) {
        var existing = serviceRecordRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("ServiceRecord", id));
        existing.setArchivedAt(Instant.now());
        serviceRecordRepository.save(existing);
    }
}
