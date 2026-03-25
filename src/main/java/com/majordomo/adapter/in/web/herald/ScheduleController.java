package com.majordomo.adapter.in.web.herald;

import com.majordomo.domain.model.herald.MaintenanceSchedule;
import com.majordomo.domain.model.herald.ServiceRecord;
import com.majordomo.domain.port.out.herald.MaintenanceScheduleRepository;
import com.majordomo.domain.port.out.herald.ServiceRecordRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for the Herald domain: manages maintenance schedules and service records
 * for properties.
 *
 * <p>Exposes schedule CRUD operations and service-record tracking under
 * {@code /api/schedules}. Acts as an inbound adapter in the hexagonal architecture,
 * delegating persistence to {@link MaintenanceScheduleRepository} and
 * {@link ServiceRecordRepository}.</p>
 */
@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {

    private final MaintenanceScheduleRepository scheduleRepository;
    private final ServiceRecordRepository serviceRecordRepository;

    /**
     * Constructs a {@code ScheduleController} with the required repositories.
     *
     * @param scheduleRepository      the port used to store and retrieve maintenance schedules
     * @param serviceRecordRepository the port used to store and retrieve service records
     */
    public ScheduleController(
            MaintenanceScheduleRepository scheduleRepository,
            ServiceRecordRepository serviceRecordRepository) {
        this.scheduleRepository = scheduleRepository;
        this.serviceRecordRepository = serviceRecordRepository;
    }

    /**
     * Returns all maintenance schedules associated with the specified property.
     *
     * @param propertyId the UUID of the property whose schedules are retrieved
     * @return a list of matching schedules; empty if none exist
     */
    @GetMapping
    public List<MaintenanceSchedule> listByProperty(@RequestParam UUID propertyId) {
        return scheduleRepository.findByPropertyId(propertyId);
    }

    /**
     * Returns all maintenance schedules due within a given number of days from today.
     *
     * @param days the lookahead window in days; defaults to 30 if not specified
     * @return a list of schedules whose due date falls before the calculated cutoff
     */
    @GetMapping("/upcoming")
    public List<MaintenanceSchedule> listUpcoming(@RequestParam(defaultValue = "30") int days) {
        return scheduleRepository.findDueBefore(LocalDate.now().plusDays(days));
    }

    /**
     * Returns a single maintenance schedule by its unique identifier.
     *
     * @param id the UUID of the schedule to retrieve
     * @return {@code 200 OK} with the schedule body, or {@code 404 Not Found} if no match exists
     */
    @GetMapping("/{id}")
    public ResponseEntity<MaintenanceSchedule> getById(@PathVariable UUID id) {
        return scheduleRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Creates a new maintenance schedule, assigning a generated ID and audit timestamps.
     *
     * @param schedule the schedule data provided in the request body
     * @return {@code 201 Created} with the persisted schedule and a {@code Location} header
     */
    @PostMapping
    public ResponseEntity<MaintenanceSchedule> create(@RequestBody MaintenanceSchedule schedule) {
        schedule.setId(UUID.randomUUID());
        schedule.setCreatedAt(Instant.now());
        schedule.setUpdatedAt(Instant.now());
        var saved = scheduleRepository.save(schedule);
        return ResponseEntity.created(URI.create("/api/schedules/" + saved.getId())).body(saved);
    }

    /**
     * Records a completed service event against an existing maintenance schedule.
     *
     * <p>The new record is linked to the given schedule ID and assigned a generated ID
     * and audit timestamps before being persisted.</p>
     *
     * @param id     the UUID of the maintenance schedule being serviced
     * @param record the service record data provided in the request body
     * @return {@code 201 Created} with the persisted service record and a {@code Location} header
     */
    @PostMapping("/{id}/records")
    public ResponseEntity<ServiceRecord> recordService(
            @PathVariable UUID id,
            @RequestBody ServiceRecord record) {
        record.setId(UUID.randomUUID());
        record.setScheduleId(id);
        record.setCreatedAt(Instant.now());
        record.setUpdatedAt(Instant.now());
        var saved = serviceRecordRepository.save(record);
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
        return serviceRecordRepository.findByScheduleId(id);
    }
}
