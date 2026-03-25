package com.majordomo.domain.port.out.herald;

import com.majordomo.domain.model.herald.ServiceRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for persisting and querying service records.
 * A service record captures the completion of a maintenance task — who did the
 * work, when, and against which schedule — forming the property's maintenance history.
 */
public interface ServiceRecordRepository {

    /**
     * Persists a service record, inserting or updating as needed.
     *
     * @param record the service record to save
     * @return the saved record, including any generated or updated fields
     */
    ServiceRecord save(ServiceRecord record);

    /**
     * Retrieves a service record by its unique identifier.
     *
     * @param id the service record ID
     * @return the service record, or empty if not found
     */
    Optional<ServiceRecord> findById(UUID id);

    /**
     * Returns the full service history for a given property.
     *
     * @param propertyId the property whose service history is sought
     * @return list of service records for that property, or an empty list if none exist
     */
    List<ServiceRecord> findByPropertyId(UUID propertyId);

    /**
     * Returns all service records logged against a specific maintenance schedule.
     * Useful for reviewing the completion history of a recurring task.
     *
     * @param scheduleId the maintenance schedule whose records are sought
     * @return list of service records for that schedule, or an empty list if none exist
     */
    List<ServiceRecord> findByScheduleId(UUID scheduleId);
}
