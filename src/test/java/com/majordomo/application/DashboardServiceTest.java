package com.majordomo.application;

import com.majordomo.domain.model.DashboardSummary;
import com.majordomo.domain.model.concierge.Contact;
import com.majordomo.domain.model.herald.MaintenanceSchedule;
import com.majordomo.domain.model.herald.ServiceRecord;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.port.out.concierge.ContactRepository;
import com.majordomo.domain.port.out.herald.MaintenanceScheduleRepository;
import com.majordomo.domain.port.out.herald.ServiceRecordRepository;
import com.majordomo.domain.port.out.ledger.LedgerQueryRepository;
import com.majordomo.domain.port.out.steward.PropertyRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private PropertyRepository propertyRepository;

    @Mock
    private ContactRepository contactRepository;

    @Mock
    private MaintenanceScheduleRepository scheduleRepository;

    @Mock
    private ServiceRecordRepository serviceRecordRepository;

    @Mock
    private LedgerQueryRepository ledgerQueryRepository;

    private DashboardService dashboardService;

    private UUID orgId;
    private UUID propertyId;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(
                propertyRepository, contactRepository,
                scheduleRepository, serviceRecordRepository,
                ledgerQueryRepository);
        orgId = UUID.randomUUID();
        propertyId = UUID.randomUUID();
    }

    @Test
    void getSummaryReturnsPropertyCount() {
        Property property = new Property();
        property.setId(propertyId);
        when(propertyRepository.findByOrganizationId(orgId)).thenReturn(List.of(property));
        when(contactRepository.findByOrganizationId(orgId)).thenReturn(List.of());
        when(scheduleRepository.findDueBefore(any(LocalDate.class))).thenReturn(List.of());
        when(serviceRecordRepository.findRecentByPropertyIds(anyList(), anyInt()))
                .thenReturn(List.of());
        when(ledgerQueryRepository.totalMaintenanceCostByOrganization(orgId))
                .thenReturn(BigDecimal.ZERO);

        DashboardSummary summary = dashboardService.getSummary(orgId);

        assertEquals(1, summary.propertyCount());
    }

    @Test
    void getSummaryReturnsContactCount() {
        Property property = new Property();
        property.setId(propertyId);
        Contact contact = new Contact();
        when(propertyRepository.findByOrganizationId(orgId)).thenReturn(List.of(property));
        when(contactRepository.findByOrganizationId(orgId))
                .thenReturn(List.of(contact, new Contact()));
        when(scheduleRepository.findDueBefore(any(LocalDate.class))).thenReturn(List.of());
        when(serviceRecordRepository.findRecentByPropertyIds(anyList(), anyInt()))
                .thenReturn(List.of());
        when(ledgerQueryRepository.totalMaintenanceCostByOrganization(orgId))
                .thenReturn(BigDecimal.ZERO);

        DashboardSummary summary = dashboardService.getSummary(orgId);

        assertEquals(2, summary.contactCount());
    }

    @Test
    void getSummaryFiltersUpcomingMaintenanceByOrgProperties() {
        Property property = new Property();
        property.setId(propertyId);
        when(propertyRepository.findByOrganizationId(orgId)).thenReturn(List.of(property));
        when(contactRepository.findByOrganizationId(orgId)).thenReturn(List.of());

        MaintenanceSchedule orgSchedule = new MaintenanceSchedule();
        orgSchedule.setPropertyId(propertyId);

        MaintenanceSchedule otherSchedule = new MaintenanceSchedule();
        otherSchedule.setPropertyId(UUID.randomUUID());

        when(scheduleRepository.findDueBefore(any(LocalDate.class)))
                .thenReturn(List.of(orgSchedule, otherSchedule));
        when(serviceRecordRepository.findRecentByPropertyIds(anyList(), anyInt()))
                .thenReturn(List.of());
        when(ledgerQueryRepository.totalMaintenanceCostByOrganization(orgId))
                .thenReturn(BigDecimal.ZERO);

        DashboardSummary summary = dashboardService.getSummary(orgId);

        assertEquals(1, summary.upcomingMaintenance().size());
        assertEquals(propertyId, summary.upcomingMaintenance().get(0).getPropertyId());
    }

    @Test
    void getSummaryFiltersOverdueItemsByOrgProperties() {
        Property property = new Property();
        property.setId(propertyId);
        when(propertyRepository.findByOrganizationId(orgId)).thenReturn(List.of(property));
        when(contactRepository.findByOrganizationId(orgId)).thenReturn(List.of());

        MaintenanceSchedule overdueSchedule = new MaintenanceSchedule();
        overdueSchedule.setPropertyId(propertyId);

        MaintenanceSchedule otherSchedule = new MaintenanceSchedule();
        otherSchedule.setPropertyId(UUID.randomUUID());

        when(scheduleRepository.findDueBefore(any(LocalDate.class)))
                .thenReturn(List.of(overdueSchedule, otherSchedule));
        when(serviceRecordRepository.findRecentByPropertyIds(anyList(), anyInt()))
                .thenReturn(List.of());
        when(ledgerQueryRepository.totalMaintenanceCostByOrganization(orgId))
                .thenReturn(BigDecimal.ZERO);

        DashboardSummary summary = dashboardService.getSummary(orgId);

        assertEquals(1, summary.overdueItems().size());
        assertEquals(propertyId, summary.overdueItems().get(0).getPropertyId());
    }

    @Test
    void getSummaryReturnsRecentServiceRecords() {
        Property property = new Property();
        property.setId(propertyId);
        when(propertyRepository.findByOrganizationId(orgId)).thenReturn(List.of(property));
        when(contactRepository.findByOrganizationId(orgId)).thenReturn(List.of());
        when(scheduleRepository.findDueBefore(any(LocalDate.class))).thenReturn(List.of());

        ServiceRecord record = new ServiceRecord();
        record.setDescription("Filter replaced");
        when(serviceRecordRepository.findRecentByPropertyIds(anyList(), eq(10)))
                .thenReturn(List.of(record));
        when(ledgerQueryRepository.totalMaintenanceCostByOrganization(orgId))
                .thenReturn(BigDecimal.ZERO);

        DashboardSummary summary = dashboardService.getSummary(orgId);

        assertEquals(1, summary.recentServiceRecords().size());
        assertEquals("Filter replaced", summary.recentServiceRecords().get(0).getDescription());
    }

    @Test
    void getSummaryReturnsTotalSpend() {
        Property property = new Property();
        property.setId(propertyId);
        when(propertyRepository.findByOrganizationId(orgId)).thenReturn(List.of(property));
        when(contactRepository.findByOrganizationId(orgId)).thenReturn(List.of());
        when(scheduleRepository.findDueBefore(any(LocalDate.class))).thenReturn(List.of());
        when(serviceRecordRepository.findRecentByPropertyIds(anyList(), anyInt()))
                .thenReturn(List.of());
        when(ledgerQueryRepository.totalMaintenanceCostByOrganization(orgId))
                .thenReturn(new BigDecimal("1500.00"));

        DashboardSummary summary = dashboardService.getSummary(orgId);

        assertEquals(new BigDecimal("1500.00"), summary.totalSpend());
    }

    @Test
    void getSummaryWithNoPropertiesReturnsEmptyResult() {
        when(propertyRepository.findByOrganizationId(orgId)).thenReturn(List.of());
        when(contactRepository.findByOrganizationId(orgId)).thenReturn(List.of());
        when(scheduleRepository.findDueBefore(any(LocalDate.class))).thenReturn(List.of());
        when(serviceRecordRepository.findRecentByPropertyIds(anyList(), anyInt()))
                .thenReturn(List.of());
        when(ledgerQueryRepository.totalMaintenanceCostByOrganization(orgId))
                .thenReturn(BigDecimal.ZERO);

        DashboardSummary summary = dashboardService.getSummary(orgId);

        assertNotNull(summary);
        assertEquals(0, summary.propertyCount());
        assertEquals(0, summary.contactCount());
        assertTrue(summary.upcomingMaintenance().isEmpty());
        assertTrue(summary.overdueItems().isEmpty());
        assertTrue(summary.recentServiceRecords().isEmpty());
        assertEquals(BigDecimal.ZERO, summary.totalSpend());
    }

    @Test
    void getSummaryDelegatesToAllRepositories() {
        Property property = new Property();
        property.setId(propertyId);
        when(propertyRepository.findByOrganizationId(orgId)).thenReturn(List.of(property));
        when(contactRepository.findByOrganizationId(orgId)).thenReturn(List.of());
        when(scheduleRepository.findDueBefore(any(LocalDate.class))).thenReturn(List.of());
        when(serviceRecordRepository.findRecentByPropertyIds(anyList(), anyInt()))
                .thenReturn(List.of());
        when(ledgerQueryRepository.totalMaintenanceCostByOrganization(orgId))
                .thenReturn(BigDecimal.ZERO);

        dashboardService.getSummary(orgId);

        verify(propertyRepository).findByOrganizationId(orgId);
        verify(contactRepository).findByOrganizationId(orgId);
        verify(scheduleRepository).findDueBefore(LocalDate.now().plusDays(30));
        verify(scheduleRepository).findDueBefore(LocalDate.now());
        verify(serviceRecordRepository).findRecentByPropertyIds(anyList(), eq(10));
        verify(ledgerQueryRepository).totalMaintenanceCostByOrganization(orgId);
    }
}
