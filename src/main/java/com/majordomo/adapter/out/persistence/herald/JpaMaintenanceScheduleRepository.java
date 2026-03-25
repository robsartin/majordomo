package com.majordomo.adapter.out.persistence.herald;

import com.majordomo.domain.model.herald.Frequency;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Searches schedules by property with a case-insensitive query and optional
     * frequency filter, ordered by ID.
     *
     * @param propertyId the property ID
     * @param query      the search term
     * @param frequency  optional frequency filter (null matches all)
     * @param pageable   pagination information
     * @return list of matching schedule entities ordered by ID
     */
    @Query("SELECT s FROM MaintenanceScheduleEntity s WHERE s.propertyId = :propId "
            + "AND LOWER(s.description) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "AND (:frequency IS NULL OR s.frequency = :frequency) "
            + "ORDER BY s.id")
    List<MaintenanceScheduleEntity> searchByPropertyIdOrderById(
            @Param("propId") UUID propertyId, @Param("q") String query,
            @Param("frequency") Frequency frequency, Pageable pageable);

    /**
     * Searches schedules by property with a case-insensitive query, optional
     * frequency filter, and cursor, ordered by ID.
     *
     * @param propertyId the property ID
     * @param query      the search term
     * @param frequency  optional frequency filter (null matches all)
     * @param cursor     the cursor ID (exclusive lower bound)
     * @param pageable   pagination information
     * @return list of matching schedule entities after the cursor, ordered by ID
     */
    @Query("SELECT s FROM MaintenanceScheduleEntity s WHERE s.propertyId = :propId "
            + "AND s.id > :cursor "
            + "AND LOWER(s.description) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "AND (:frequency IS NULL OR s.frequency = :frequency) "
            + "ORDER BY s.id")
    List<MaintenanceScheduleEntity> searchByPropertyIdAndIdGreaterThanOrderById(
            @Param("propId") UUID propertyId, @Param("q") String query,
            @Param("frequency") Frequency frequency, @Param("cursor") UUID cursor,
            Pageable pageable);
}
