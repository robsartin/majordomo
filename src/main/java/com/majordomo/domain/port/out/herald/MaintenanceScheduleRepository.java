package com.majordomo.domain.port.out.herald;

import com.majordomo.domain.model.herald.MaintenanceSchedule;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for persisting and querying maintenance schedules.
 * A maintenance schedule defines a recurring or one-time task that must be
 * performed on a property (e.g. HVAC filter replacement, annual inspection).
 */
public interface MaintenanceScheduleRepository {

    /**
     * Persists a maintenance schedule, inserting or updating as needed.
     *
     * @param schedule the schedule to save
     * @return the saved schedule, including any generated or updated fields
     */
    MaintenanceSchedule save(MaintenanceSchedule schedule);

    /**
     * Retrieves a maintenance schedule by its unique identifier.
     *
     * @param id the schedule ID
     * @return the schedule, or empty if not found
     */
    Optional<MaintenanceSchedule> findById(UUID id);

    /**
     * Returns all maintenance schedules defined for a given property.
     *
     * @param propertyId the property whose schedules are sought
     * @return list of schedules for that property, or an empty list if none exist
     */
    List<MaintenanceSchedule> findByPropertyId(UUID propertyId);

    /**
     * Returns schedules for a property with cursor-based pagination.
     *
     * @param propertyId the property ID
     * @param cursor     exclusive start cursor (null for first page)
     * @param limit      maximum number of results
     * @return list of schedules after the cursor, ordered by ID
     */
    List<MaintenanceSchedule> findByPropertyId(UUID propertyId, UUID cursor, int limit);

    /**
     * Returns all maintenance schedules whose next due date falls before the given date.
     * Intended for surfacing overdue or imminently due tasks.
     *
     * @param date the exclusive upper bound for the due date
     * @return list of schedules due before that date, or an empty list if none qualify
     */
    List<MaintenanceSchedule> findDueBefore(LocalDate date);
}
