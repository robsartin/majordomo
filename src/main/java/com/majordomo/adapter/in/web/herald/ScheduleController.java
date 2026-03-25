package com.majordomo.adapter.in.web.herald;

import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.herald.MaintenanceSchedule;
import com.majordomo.domain.model.herald.ServiceRecord;
import com.majordomo.domain.port.in.herald.ManageScheduleUseCase;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for the Herald domain: manages maintenance schedules and service records
 * for properties.
 *
 * <p>Exposes schedule CRUD operations and service-record tracking under
 * {@code /api/schedules}. Acts as an inbound adapter in the hexagonal architecture,
 * delegating to {@link ManageScheduleUseCase}.</p>
 */
@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {

    private final ManageScheduleUseCase scheduleUseCase;

    /**
     * Constructs a {@code ScheduleController} with the required use case.
     *
     * @param scheduleUseCase the inbound port for schedule management
     */
    public ScheduleController(ManageScheduleUseCase scheduleUseCase) {
        this.scheduleUseCase = scheduleUseCase;
    }

    /**
     * Returns schedules associated with the specified property with cursor-based pagination.
     *
     * @param propertyId the UUID of the property whose schedules are retrieved
     * @param cursor     optional cursor for the next page (exclusive start)
     * @param limit      maximum number of results per page (default 20)
     * @return a page of matching schedules
     */
    @GetMapping
    public Page<MaintenanceSchedule> listByProperty(
            @RequestParam UUID propertyId,
            @RequestParam(required = false) UUID cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return scheduleUseCase.findByPropertyId(propertyId, cursor, limit);
    }

    /**
     * Returns all maintenance schedules due within a given number of days from today.
     *
     * @param days the lookahead window in days; defaults to 30 if not specified
     * @return a list of schedules whose due date falls before the calculated cutoff
     */
    @GetMapping("/upcoming")
    public List<MaintenanceSchedule> listUpcoming(@RequestParam(defaultValue = "30") int days) {
        return scheduleUseCase.findDueBefore(LocalDate.now().plusDays(days));
    }

    /**
     * Returns a single maintenance schedule by its unique identifier.
     *
     * @param id the UUID of the schedule to retrieve
     * @return {@code 200 OK} with the schedule body, or {@code 404 Not Found} if no match exists
     */
    @GetMapping("/{id}")
    public ResponseEntity<MaintenanceSchedule> getById(@PathVariable UUID id) {
        return scheduleUseCase.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Creates a new maintenance schedule, delegating ID generation and timestamps
     * to the service layer.
     *
     * @param schedule the schedule data provided in the request body
     * @return {@code 201 Created} with the persisted schedule and a {@code Location} header
     */
    @PostMapping
    public ResponseEntity<MaintenanceSchedule> create(@RequestBody MaintenanceSchedule schedule) {
        var saved = scheduleUseCase.create(schedule);
        return ResponseEntity.created(URI.create("/api/schedules/" + saved.getId())).body(saved);
    }

    /**
     * Records a completed service event against an existing maintenance schedule.
     *
     * <p>The new record is linked to the given schedule ID by the service layer and assigned
     * a generated ID and audit timestamps before being persisted.</p>
     *
     * @param id     the UUID of the maintenance schedule being serviced
     * @param record the service record data provided in the request body
     * @return {@code 201 Created} with the persisted service record and a {@code Location} header
     */
    @PostMapping("/{id}/records")
    public ResponseEntity<ServiceRecord> recordService(
            @PathVariable UUID id,
            @RequestBody ServiceRecord record) {
        var saved = scheduleUseCase.recordService(id, record);
        return ResponseEntity.created(URI.create("/api/schedules/" + id + "/records/" + saved.getId())).body(saved);
    }

    /**
     * Returns all service records associated with the specified maintenance schedule.
     *
     * @param id the UUID of the maintenance schedule whose records are retrieved
     * @return a list of service records; empty if none have been recorded
     */
    @GetMapping("/{id}/records")
    public List<ServiceRecord> listRecords(@PathVariable UUID id) {
        return scheduleUseCase.findRecordsByScheduleId(id);
    }

    /**
     * Updates an existing maintenance schedule, preserving its ID and creation timestamp.
     *
     * @param id       the UUID of the schedule to update
     * @param schedule the updated schedule data provided in the request body
     * @return {@code 200 OK} with the updated schedule
     */
    @PutMapping("/{id}")
    public ResponseEntity<MaintenanceSchedule> update(
            @PathVariable UUID id,
            @RequestBody MaintenanceSchedule schedule) {
        var updated = scheduleUseCase.update(id, schedule);
        return ResponseEntity.ok(updated);
    }

    /**
     * Archives a maintenance schedule by setting its archived_at timestamp (soft delete).
     *
     * @param id the UUID of the schedule to archive
     * @return {@code 204 No Content} on success
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archive(@PathVariable UUID id) {
        scheduleUseCase.archive(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Updates an existing service record, preserving its ID and creation timestamp.
     *
     * @param id       the UUID of the schedule the record belongs to (unused in lookup)
     * @param recordId the UUID of the service record to update
     * @param record   the updated service record data provided in the request body
     * @return {@code 200 OK} with the updated service record
     */
    @PutMapping("/{id}/records/{recordId}")
    public ResponseEntity<ServiceRecord> updateRecord(
            @PathVariable UUID id,
            @PathVariable UUID recordId,
            @RequestBody ServiceRecord record) {
        var updated = scheduleUseCase.updateRecord(recordId, record);
        return ResponseEntity.ok(updated);
    }

    /**
     * Archives a service record by setting its archived_at timestamp (soft delete).
     *
     * @param id       the UUID of the schedule the record belongs to (unused in lookup)
     * @param recordId the UUID of the service record to archive
     * @return {@code 204 No Content} on success
     */
    @DeleteMapping("/{id}/records/{recordId}")
    public ResponseEntity<Void> archiveRecord(
            @PathVariable UUID id,
            @PathVariable UUID recordId) {
        scheduleUseCase.archiveRecord(recordId);
        return ResponseEntity.noContent().build();
    }
}
