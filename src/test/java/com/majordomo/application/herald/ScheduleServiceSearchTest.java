package com.majordomo.application.herald;

import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.herald.MaintenanceSchedule;
import com.majordomo.domain.port.out.herald.MaintenanceScheduleRepository;
import com.majordomo.domain.port.out.herald.ServiceRecordRepository;
import com.majordomo.domain.port.out.steward.PropertyRepository;
import com.majordomo.domain.port.out.EventPublisher;

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

/**
 * Unit tests for schedule search functionality in {@link ScheduleService}.
 */
@ExtendWith(MockitoExtension.class)
class ScheduleServiceSearchTest {

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
    void searchReturnsPageWithCursorWhenMoreResults() {
        UUID propId = UUID.randomUUID();
        String query = "filter";
        List<MaintenanceSchedule> threeSchedules = createSchedules(3);
        when(scheduleRepository.search(propId, query, null, null, 3))
                .thenReturn(threeSchedules);

        Page<MaintenanceSchedule> page = scheduleService.search(propId, query, null, null, 2);

        assertEquals(2, page.items().size());
        assertTrue(page.hasMore());
        assertNotNull(page.nextCursor());
        assertEquals(threeSchedules.get(1).getId(), page.nextCursor());
        verify(scheduleRepository).search(propId, query, null, null, 3);
    }

    @Test
    void searchReturnsLastPageWithNoMoreResults() {
        UUID propId = UUID.randomUUID();
        UUID cursor = UUID.randomUUID();
        String query = "inspection";
        List<MaintenanceSchedule> oneSchedule = createSchedules(1);
        when(scheduleRepository.search(propId, query, null, cursor, 3))
                .thenReturn(oneSchedule);

        Page<MaintenanceSchedule> page = scheduleService.search(propId, query, null, cursor, 2);

        assertEquals(1, page.items().size());
        assertFalse(page.hasMore());
        assertNull(page.nextCursor());
        verify(scheduleRepository).search(propId, query, null, cursor, 3);
    }

    @Test
    void searchPassesFrequencyFilter() {
        UUID propId = UUID.randomUUID();
        String query = "hvac";
        String frequency = "MONTHLY";
        when(scheduleRepository.search(propId, query, frequency, null, 21))
                .thenReturn(createSchedules(5));

        Page<MaintenanceSchedule> page = scheduleService.search(
                propId, query, frequency, null, 20);

        assertEquals(5, page.items().size());
        assertFalse(page.hasMore());
        verify(scheduleRepository).search(propId, query, frequency, null, 21);
    }

    @Test
    void searchClampsLimitToMax100() {
        UUID propId = UUID.randomUUID();
        String query = "test";
        when(scheduleRepository.search(propId, query, null, null, 101))
                .thenReturn(createSchedules(101));

        Page<MaintenanceSchedule> page = scheduleService.search(
                propId, query, null, null, 200);

        assertEquals(100, page.items().size());
        assertTrue(page.hasMore());
        verify(scheduleRepository).search(propId, query, null, null, 101);
    }

    @Test
    void searchReturnsEmptyPageWhenNoResults() {
        UUID propId = UUID.randomUUID();
        String query = "nonexistent";
        when(scheduleRepository.search(propId, query, null, null, 21))
                .thenReturn(List.of());

        Page<MaintenanceSchedule> page = scheduleService.search(
                propId, query, null, null, 20);

        assertTrue(page.items().isEmpty());
        assertFalse(page.hasMore());
        assertNull(page.nextCursor());
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
