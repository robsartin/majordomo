package com.majordomo.adapter.out.persistence.herald;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link MaintenanceScheduleEntity}, providing persistence operations
 * used by {@link MaintenanceScheduleRepositoryAdapter}.
 */
public interface JpaMaintenanceScheduleRepository extends JpaRepository<MaintenanceScheduleEntity, UUID>,
        JpaSpecificationExecutor<MaintenanceScheduleEntity> {

    /**
     * Returns all schedules for a property (unbounded, for internal use).
     *
     * @param propertyId the property ID
     * @return list of all schedule entities for the property
     */
    List<MaintenanceScheduleEntity> findByPropertyId(UUID propertyId);

    /**
     * Returns all schedules whose next due date is before the given date.
     *
     * @param date the upper bound date
     * @return list of matching schedule entities
     */
    List<MaintenanceScheduleEntity> findByNextDueBefore(LocalDate date);
}
