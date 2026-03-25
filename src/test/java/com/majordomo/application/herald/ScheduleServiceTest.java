package com.majordomo.application.herald;

import com.majordomo.domain.model.herald.MaintenanceSchedule;
import com.majordomo.domain.model.herald.ServiceRecord;
import com.majordomo.domain.port.out.herald.MaintenanceScheduleRepository;
import com.majordomo.domain.port.out.herald.ServiceRecordRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    @Mock
    private MaintenanceScheduleRepository scheduleRepository;

    @Mock
    private ServiceRecordRepository serviceRecordRepository;

    private ScheduleService scheduleService;

    @BeforeEach
    void setUp() {
        scheduleService = new ScheduleService(scheduleRepository, serviceRecordRepository);
    }

    @Test
    void createSetsIdAndTimestamps() {
        var schedule = new MaintenanceSchedule();
        schedule.setDescription("HVAC filter replacement");

        when(scheduleRepository.save(any(MaintenanceSchedule.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var result = scheduleService.create(schedule);

        assertNotNull(result.getId());
        assertNotNull(result.getCreatedAt());
        assertNotNull(result.getUpdatedAt());

        ArgumentCaptor<MaintenanceSchedule> captor =
                ArgumentCaptor.forClass(MaintenanceSchedule.class);
        verify(scheduleRepository).save(captor.capture());
        assertNotNull(captor.getValue().getId());
        assertNotNull(captor.getValue().getCreatedAt());
        assertNotNull(captor.getValue().getUpdatedAt());
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
        assertNotNull(result.getCreatedAt());
        assertNotNull(result.getUpdatedAt());

        ArgumentCaptor<ServiceRecord> captor = ArgumentCaptor.forClass(ServiceRecord.class);
        verify(serviceRecordRepository).save(captor.capture());
        assertEquals(scheduleId, captor.getValue().getScheduleId());
    }
}
