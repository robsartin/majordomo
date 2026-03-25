package com.majordomo.adapter.out.persistence.herald;

import org.springframework.data.domain.Pageable;
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

    /**
     * Returns schedules for a property ordered by ID.
     *
     * @param propertyId the property ID
     * @param pageable   pagination information
     * @return list of schedule entities ordered by ID
     */
    List<MaintenanceScheduleEntity> findByPropertyIdOrderById(UUID propertyId, Pageable pageable);

    /**
     * Returns schedules for a property with ID greater than the given cursor, ordered by ID.
     *
     * @param propertyId the property ID
     * @param id         the cursor ID (exclusive lower bound)
     * @param pageable   pagination information
     * @return list of schedule entities after the cursor, ordered by ID
     */
    List<MaintenanceScheduleEntity> findByPropertyIdAndIdGreaterThanOrderById(
            UUID propertyId, UUID id, Pageable pageable);
}
