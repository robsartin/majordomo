package com.majordomo.adapter.out.persistence.herald;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Returns non-archived schedules for properties in an organization due on or
     * after {@code from}, soonest first. Joins each schedule to its property to
     * apply the org scope (schedules carry no organization id of their own).
     *
     * @param organizationId the organization scope
     * @param from           inclusive lower bound for {@code nextDue}
     * @return matching schedule entities, ordered by due date
     */
    @Query("""
            SELECT s FROM MaintenanceScheduleEntity s, PropertyEntity p
             WHERE s.propertyId = p.id
               AND p.organizationId = :organizationId
               AND s.nextDue >= :from
               AND s.archivedAt IS NULL
             ORDER BY s.nextDue
            """)
    List<MaintenanceScheduleEntity> findUpcomingByOrganizationId(
            @Param("organizationId") UUID organizationId, @Param("from") LocalDate from);
}
