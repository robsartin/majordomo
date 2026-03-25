package com.majordomo.domain.port.in.herald;

import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.herald.MaintenanceSchedule;
import com.majordomo.domain.model.herald.ServiceRecord;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Inbound port for managing maintenance schedules and service records in the Herald domain.
 */
public interface ManageScheduleUseCase {

    /**
     * Creates a new maintenance schedule.
     *
     * @param schedule the schedule to create
     * @return the created schedule with generated ID and timestamps
     */
    MaintenanceSchedule create(MaintenanceSchedule schedule);

    /**
     * Finds a maintenance schedule by ID.
     *
     * @param id the schedule ID
     * @return the schedule, or empty if not found
     */
    Optional<MaintenanceSchedule> findById(UUID id);

    /**
     * Lists all schedules for a property.
     *
     * @param propertyId the property ID
     * @return list of schedules
     */
    List<MaintenanceSchedule> findByPropertyId(UUID propertyId);

    /**
     * Lists schedules for a property with cursor-based pagination.
     *
     * @param propertyId the property ID
     * @param cursor     the cursor UUID (null for first page)
     * @param limit      max results per page (1-100)
     * @return a page of schedules
     */
    Page<MaintenanceSchedule> findByPropertyId(UUID propertyId, UUID cursor, int limit);

    /**
     * Lists all schedules due before the given date.
     *
     * @param date the exclusive upper bound for the due date
     * @return list of schedules due before that date
     */
    List<MaintenanceSchedule> findDueBefore(LocalDate date);

    /**
     * Records a completed service event against an existing schedule.
     *
     * @param scheduleId the ID of the maintenance schedule being serviced
     * @param record     the service record data
     * @return the persisted service record with generated ID, linked schedule, and timestamps
     */
    ServiceRecord recordService(UUID scheduleId, ServiceRecord record);

    /**
     * Lists all service records for a given schedule.
     *
     * @param scheduleId the maintenance schedule ID
     * @return list of service records
     */
    List<ServiceRecord> findRecordsByScheduleId(UUID scheduleId);

    /**
     * Updates an existing maintenance schedule.
     *
     * @param id       the schedule ID
     * @param schedule the updated schedule data
     * @return the updated schedule
     */
    MaintenanceSchedule update(UUID id, MaintenanceSchedule schedule);

    /**
     * Archives a maintenance schedule by setting archived_at.
     *
     * @param id the schedule ID
     */
    void archive(UUID id);

    /**
     * Updates an existing service record.
     *
     * @param id     the service record ID
     * @param record the updated service record data
     * @return the updated service record
     */
    ServiceRecord updateRecord(UUID id, ServiceRecord record);

    /**
     * Archives a service record by setting archived_at.
     *
     * @param id the service record ID
     */
    void archiveRecord(UUID id);
}
