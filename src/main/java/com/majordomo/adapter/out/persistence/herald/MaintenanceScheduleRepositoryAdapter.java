package com.majordomo.adapter.out.persistence.herald;

import com.majordomo.domain.model.herald.Frequency;
import com.majordomo.domain.model.herald.MaintenanceSchedule;
import com.majordomo.domain.port.out.herald.MaintenanceScheduleRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence adapter that fulfills the {@link com.majordomo.domain.port.out.herald.MaintenanceScheduleRepository}
 * output port by delegating to {@link JpaMaintenanceScheduleRepository}.
 */
@Repository
public class MaintenanceScheduleRepositoryAdapter implements MaintenanceScheduleRepository {

    private final JpaMaintenanceScheduleRepository jpa;

    public MaintenanceScheduleRepositoryAdapter(JpaMaintenanceScheduleRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public MaintenanceSchedule save(MaintenanceSchedule schedule) {
        var entity = MaintenanceScheduleMapper.toEntity(schedule);
        return MaintenanceScheduleMapper.toDomain(jpa.save(entity));
    }

    @Override
    public Optional<MaintenanceSchedule> findById(UUID id) {
        return jpa.findById(id).map(MaintenanceScheduleMapper::toDomain);
    }

    @Override
    public List<MaintenanceSchedule> findByPropertyId(UUID propertyId) {
        return jpa.findByPropertyId(propertyId).stream().map(MaintenanceScheduleMapper::toDomain).toList();
    }

    @Override
    public List<MaintenanceSchedule> findByPropertyId(UUID propertyId, UUID cursor, int limit) {
        List<MaintenanceScheduleEntity> entities;
        if (cursor == null) {
            entities = jpa.findByPropertyIdOrderById(propertyId, PageRequest.of(0, limit));
        } else {
            entities = jpa.findByPropertyIdAndIdGreaterThanOrderById(
                    propertyId, cursor, PageRequest.of(0, limit));
        }
        return entities.stream().map(MaintenanceScheduleMapper::toDomain).toList();
    }

    @Override
    public List<MaintenanceSchedule> findDueBefore(LocalDate date) {
        return jpa.findByNextDueBefore(date).stream().map(MaintenanceScheduleMapper::toDomain).toList();
    }

    @Override
    public List<MaintenanceSchedule> search(UUID propertyId, String query, String frequency,
                                            UUID cursor, int limit) {
        var freqEnum = frequency != null ? Frequency.valueOf(frequency) : null;
        List<MaintenanceScheduleEntity> entities;
        if (cursor == null) {
            entities = jpa.searchByPropertyIdOrderById(
                    propertyId, query, freqEnum, PageRequest.of(0, limit));
        } else {
            entities = jpa.searchByPropertyIdAndIdGreaterThanOrderById(
                    propertyId, query, freqEnum, cursor, PageRequest.of(0, limit));
        }
        return entities.stream().map(MaintenanceScheduleMapper::toDomain).toList();
    }
}
