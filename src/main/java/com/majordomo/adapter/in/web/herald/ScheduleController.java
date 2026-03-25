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

@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {

    private final MaintenanceScheduleRepository scheduleRepository;
    private final ServiceRecordRepository serviceRecordRepository;

    public ScheduleController(
            MaintenanceScheduleRepository scheduleRepository,
            ServiceRecordRepository serviceRecordRepository) {
        this.scheduleRepository = scheduleRepository;
        this.serviceRecordRepository = serviceRecordRepository;
    }

    @GetMapping
    public List<MaintenanceSchedule> listByProperty(@RequestParam UUID propertyId) {
        return scheduleRepository.findByPropertyId(propertyId);
    }

    @GetMapping("/upcoming")
    public List<MaintenanceSchedule> listUpcoming(@RequestParam(defaultValue = "30") int days) {
        return scheduleRepository.findDueBefore(LocalDate.now().plusDays(days));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MaintenanceSchedule> getById(@PathVariable UUID id) {
        return scheduleRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<MaintenanceSchedule> create(@RequestBody MaintenanceSchedule schedule) {
        schedule.setId(UUID.randomUUID());
        schedule.setCreatedAt(Instant.now());
        schedule.setUpdatedAt(Instant.now());
        var saved = scheduleRepository.save(schedule);
        return ResponseEntity.created(URI.create("/api/schedules/" + saved.getId())).body(saved);
    }

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

    @GetMapping("/{id}/records")
    public List<ServiceRecord> listRecords(@PathVariable UUID id) {
        return serviceRecordRepository.findByScheduleId(id);
    }
}
