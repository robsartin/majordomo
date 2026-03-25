package com.majordomo.adapter.out.persistence.herald;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link MaintenanceScheduleEntity}, providing persistence operations
 * used by {@link MaintenanceScheduleRepositoryAdapter}.
 */
public interface JpaMaintenanceScheduleRepository extends JpaRepository<MaintenanceScheduleEntity, UUID> {

    List<MaintenanceScheduleEntity> findByPropertyId(UUID propertyId);

    List<MaintenanceScheduleEntity> findByNextDueBefore(LocalDate date);
}
