package com.majordomo.application.herald;

import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.herald.MaintenanceSchedule;
import com.majordomo.domain.port.out.EventPublisher;
import com.majordomo.domain.port.out.herald.MaintenanceScheduleRepository;
import com.majordomo.domain.port.out.herald.ServiceRecordRepository;
import com.majordomo.domain.port.out.steward.PropertyRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleServicePaginationTest {

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
    void findByPropertyIdFirstPageReturnsItemsAndCursor() {
        UUID propertyId = UUID.randomUUID();
        List<MaintenanceSchedule> threeSchedules = createSchedules(3);
        when(scheduleRepository.findByPropertyId(propertyId, null, 3)).thenReturn(threeSchedules);

        Page<MaintenanceSchedule> page = scheduleService.findByPropertyId(propertyId, null, 2);

        assertEquals(2, page.items().size());
        assertTrue(page.hasMore());
        assertNotNull(page.nextCursor());
        assertEquals(threeSchedules.get(1).getId(), page.nextCursor());
        verify(scheduleRepository).findByPropertyId(propertyId, null, 3);
    }

    @Test
    void findByPropertyIdLastPageHasMoreFalse() {
        UUID propertyId = UUID.randomUUID();
        UUID cursor = UUID.randomUUID();
        List<MaintenanceSchedule> oneSchedule = createSchedules(1);
        when(scheduleRepository.findByPropertyId(propertyId, cursor, 3)).thenReturn(oneSchedule);

        Page<MaintenanceSchedule> page = scheduleService.findByPropertyId(propertyId, cursor, 2);

        assertEquals(1, page.items().size());
        assertFalse(page.hasMore());
        assertNull(page.nextCursor());
        verify(scheduleRepository).findByPropertyId(propertyId, cursor, 3);
    }

    @Test
    void findByPropertyIdLimitClampedMaxIs100() {
        UUID propertyId = UUID.randomUUID();
        when(scheduleRepository.findByPropertyId(propertyId, null, 101))
                .thenReturn(createSchedules(101));

        Page<MaintenanceSchedule> page = scheduleService.findByPropertyId(propertyId, null, 200);

        assertEquals(100, page.items().size());
        assertTrue(page.hasMore());
        verify(scheduleRepository).findByPropertyId(propertyId, null, 101);
    }

    private List<MaintenanceSchedule> createSchedules(int count) {
        List<MaintenanceSchedule> schedules = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            var schedule = new MaintenanceSchedule();
            schedule.setId(UUID.randomUUID());
            schedule.setDescription("Schedule " + i);
            schedules.add(schedule);
        }
        return schedules;
    }
}
