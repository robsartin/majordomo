package com.majordomo.application.herald;

import com.majordomo.domain.model.EntityNotFoundException;
import com.majordomo.domain.model.herald.MaintenanceSchedule;
import com.majordomo.domain.model.herald.ServiceRecord;
import com.majordomo.domain.port.out.EventPublisher;
import com.majordomo.domain.port.out.herald.MaintenanceScheduleRepository;
import com.majordomo.domain.port.out.herald.ServiceRecordRepository;
import com.majordomo.domain.port.out.steward.PropertyRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    @Mock
    private MaintenanceScheduleRepository scheduleRepository;

    @Mock
    private ServiceRecordRepository serviceRecordRepository;

    @Mock
    private EventPublisher eventPublisher;

    private ScheduleService scheduleService;

    @BeforeEach
    void setUp() {
        scheduleService = new ScheduleService(scheduleRepository,
                serviceRecordRepository, mock(PropertyRepository.class), eventPublisher);
    }

    @Test
    void createSetsIdAndTimestamps() {
        var schedule = new MaintenanceSchedule();
        schedule.setDescription("HVAC filter replacement");

        when(scheduleRepository.save(any(MaintenanceSchedule.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var result = scheduleService.create(schedule);

        assertNotNull(result.getId());

        ArgumentCaptor<MaintenanceSchedule> captor =
                ArgumentCaptor.forClass(MaintenanceSchedule.class);
        verify(scheduleRepository).save(captor.capture());
        assertNotNull(captor.getValue().getId());
    }

    @Test
    void recordServiceLinksScheduleId() {
        UUID scheduleId = UUID.randomUUID();
        var record = new ServiceRecord();
        record.setDescription("Filter replaced");

        when(serviceRecordRepository.save(any(ServiceRecord.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var result = scheduleService.recordService(scheduleId, record);

        assertNotNull(result.getId());
        assertEquals(scheduleId, result.getScheduleId());

        ArgumentCaptor<ServiceRecord> captor = ArgumentCaptor.forClass(ServiceRecord.class);
        verify(serviceRecordRepository).save(captor.capture());
        assertEquals(scheduleId, captor.getValue().getScheduleId());
    }

    @Test
    void updateExistingScheduleUpdatesAndSaves() {
        UUID id = UUID.randomUUID();
        Instant originalCreatedAt = Instant.parse("2025-01-01T00:00:00Z");

        var existing = new MaintenanceSchedule();
        existing.setId(id);
        existing.setCreatedAt(originalCreatedAt);
        existing.setDescription("Old Description");

        var updated = new MaintenanceSchedule();
        updated.setDescription("New Description");

        when(scheduleRepository.findById(id)).thenReturn(Optional.of(existing));
        when(scheduleRepository.save(any(MaintenanceSchedule.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var result = scheduleService.update(id, updated);

        assertEquals(id, result.getId());
        assertEquals(originalCreatedAt, result.getCreatedAt());
        assertEquals("New Description", result.getDescription());
        verify(scheduleRepository).save(updated);
    }

    @Test
    void updateNonexistentScheduleThrowsEntityNotFoundException() {
        UUID id = UUID.randomUUID();
        when(scheduleRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> scheduleService.update(id, new MaintenanceSchedule()));
    }

    @Test
    void archiveExistingScheduleSetsArchivedAt() {
        UUID id = UUID.randomUUID();
        var existing = new MaintenanceSchedule();
        existing.setId(id);

        when(scheduleRepository.findById(id)).thenReturn(Optional.of(existing));
        when(scheduleRepository.save(any(MaintenanceSchedule.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        scheduleService.archive(id);

        ArgumentCaptor<MaintenanceSchedule> captor =
                ArgumentCaptor.forClass(MaintenanceSchedule.class);
        verify(scheduleRepository).save(captor.capture());
        assertNotNull(captor.getValue().getArchivedAt());
    }

    @Test
    void archiveNonexistentScheduleThrowsEntityNotFoundException() {
        UUID id = UUID.randomUUID();
        when(scheduleRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> scheduleService.archive(id));
    }

    @Test
    void updateRecordExistingRecordUpdatesAndSaves() {
        UUID id = UUID.randomUUID();
        Instant originalCreatedAt = Instant.parse("2025-01-01T00:00:00Z");

        var existing = new ServiceRecord();
        existing.setId(id);
        existing.setCreatedAt(originalCreatedAt);
        existing.setDescription("Old Description");

        var updated = new ServiceRecord();
        updated.setDescription("New Description");

        when(serviceRecordRepository.findById(id)).thenReturn(Optional.of(existing));
        when(serviceRecordRepository.save(any(ServiceRecord.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var result = scheduleService.updateRecord(id, updated);

        assertEquals(id, result.getId());
        assertEquals(originalCreatedAt, result.getCreatedAt());
        assertEquals("New Description", result.getDescription());
        verify(serviceRecordRepository).save(updated);
    }

    @Test
    void updateRecordNonexistentThrowsEntityNotFoundException() {
        UUID id = UUID.randomUUID();
        when(serviceRecordRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> scheduleService.updateRecord(id, new ServiceRecord()));
    }

    @Test
    void archiveRecordExistingSetsArchivedAt() {
        UUID id = UUID.randomUUID();
        var existing = new ServiceRecord();
        existing.setId(id);

        when(serviceRecordRepository.findById(id)).thenReturn(Optional.of(existing));
        when(serviceRecordRepository.save(any(ServiceRecord.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        scheduleService.archiveRecord(id);

        ArgumentCaptor<ServiceRecord> captor = ArgumentCaptor.forClass(ServiceRecord.class);
        verify(serviceRecordRepository).save(captor.capture());
        assertNotNull(captor.getValue().getArchivedAt());
    }

    @Test
    void archiveRecordNonexistentThrowsEntityNotFoundException() {
        UUID id = UUID.randomUUID();
        when(serviceRecordRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> scheduleService.archiveRecord(id));
    }
}
