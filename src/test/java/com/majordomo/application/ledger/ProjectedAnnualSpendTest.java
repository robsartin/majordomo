package com.majordomo.application.ledger;

import com.majordomo.domain.model.herald.Frequency;
import com.majordomo.domain.model.herald.MaintenanceSchedule;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.port.out.herald.MaintenanceScheduleRepository;
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
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LedgerService#projectedAnnualSpend(UUID)}, covering all
 * standard frequencies and the CUSTOM interval calculation.
 */
@ExtendWith(MockitoExtension.class)
class ProjectedAnnualSpendTest {

    @Mock
    private PropertyRepository propertyRepository;

    @Mock
    private LedgerQueryRepository ledgerQueryRepository;

    @Mock
    private MaintenanceScheduleRepository maintenanceScheduleRepository;

    private LedgerService ledgerService;

    private UUID orgId;
    private UUID propertyId;
    private Property property;

    /** Sets up the service and shared fixtures before each test. */
    @BeforeEach
    void setUp() {
        ledgerService = new LedgerService(propertyRepository,
                ledgerQueryRepository, maintenanceScheduleRepository);
        orgId = UUID.randomUUID();
        propertyId = UUID.randomUUID();
        property = new Property();
        property.setId(propertyId);
    }

    /** Weekly schedule: cost * 52. */
    @Test
    void weeklyFrequencyMultipliesBy52() {
        MaintenanceSchedule schedule = schedule(Frequency.WEEKLY, null,
                new BigDecimal("10.00"));
        stubOrgWithSchedules(List.of(schedule));

        BigDecimal result = ledgerService.projectedAnnualSpend(orgId);

        assertEquals(new BigDecimal("520.00"), result);
    }

    /** Monthly schedule: cost * 12. */
    @Test
    void monthlyFrequencyMultipliesBy12() {
        MaintenanceSchedule schedule = schedule(Frequency.MONTHLY, null,
                new BigDecimal("50.00"));
        stubOrgWithSchedules(List.of(schedule));

        BigDecimal result = ledgerService.projectedAnnualSpend(orgId);

        assertEquals(new BigDecimal("600.00"), result);
    }

    /** Quarterly schedule: cost * 4. */
    @Test
    void quarterlyFrequencyMultipliesBy4() {
        MaintenanceSchedule schedule = schedule(Frequency.QUARTERLY, null,
                new BigDecimal("100.00"));
        stubOrgWithSchedules(List.of(schedule));

        BigDecimal result = ledgerService.projectedAnnualSpend(orgId);

        assertEquals(new BigDecimal("400.00"), result);
    }

    /** Semi-annual schedule: cost * 2. */
    @Test
    void semiAnnualFrequencyMultipliesBy2() {
        MaintenanceSchedule schedule = schedule(Frequency.SEMI_ANNUAL, null,
                new BigDecimal("200.00"));
        stubOrgWithSchedules(List.of(schedule));

        BigDecimal result = ledgerService.projectedAnnualSpend(orgId);

        assertEquals(new BigDecimal("400.00"), result);
    }

    /** Annual schedule: cost * 1. */
    @Test
    void annualFrequencyMultipliesBy1() {
        MaintenanceSchedule schedule = schedule(Frequency.ANNUAL, null,
                new BigDecimal("1000.00"));
        stubOrgWithSchedules(List.of(schedule));

        BigDecimal result = ledgerService.projectedAnnualSpend(orgId);

        assertEquals(new BigDecimal("1000.00"), result);
    }

    /** Custom 30-day interval: ceil(365/30) = 13 occurrences. */
    @Test
    void customIntervalRoundsUpOccurrences() {
        MaintenanceSchedule schedule = schedule(Frequency.CUSTOM, 30,
                new BigDecimal("10.00"));
        stubOrgWithSchedules(List.of(schedule));

        BigDecimal result = ledgerService.projectedAnnualSpend(orgId);

        // ceil(365 / 30) = 13
        assertEquals(new BigDecimal("130.00"), result);
    }

    /** Custom 365-day interval: exactly 1 occurrence per year. */
    @Test
    void customInterval365DaysGivesOneOccurrence() {
        MaintenanceSchedule schedule = schedule(Frequency.CUSTOM, 365,
                new BigDecimal("500.00"));
        stubOrgWithSchedules(List.of(schedule));

        BigDecimal result = ledgerService.projectedAnnualSpend(orgId);

        assertEquals(new BigDecimal("500.00"), result);
    }

    /** Custom 7-day interval: exactly 52 occurrences per year (ceil(365/7)=53). */
    @Test
    void customInterval7DaysCeilsCorrectly() {
        MaintenanceSchedule schedule = schedule(Frequency.CUSTOM, 7,
                new BigDecimal("10.00"));
        stubOrgWithSchedules(List.of(schedule));

        // ceil(365 / 7) = ceil(52.14...) = 53
        BigDecimal result = ledgerService.projectedAnnualSpend(orgId);

        assertEquals(new BigDecimal("530.00"), result);
    }

    /** Schedules with null estimatedCost are excluded from the total. */
    @Test
    void schedulesWithNullEstimatedCostAreIgnored() {
        MaintenanceSchedule withCost = schedule(Frequency.ANNUAL, null,
                new BigDecimal("100.00"));
        MaintenanceSchedule withoutCost = schedule(Frequency.MONTHLY, null, null);
        stubOrgWithSchedules(List.of(withCost, withoutCost));

        BigDecimal result = ledgerService.projectedAnnualSpend(orgId);

        assertEquals(new BigDecimal("100.00"), result);
    }

    /** Multiple schedules across a single property are summed correctly. */
    @Test
    void multipleSchedulesAreSummed() {
        MaintenanceSchedule weekly = schedule(Frequency.WEEKLY, null,
                new BigDecimal("5.00"));
        MaintenanceSchedule annual = schedule(Frequency.ANNUAL, null,
                new BigDecimal("100.00"));
        stubOrgWithSchedules(List.of(weekly, annual));

        // 5 * 52 + 100 * 1 = 260 + 100 = 360
        BigDecimal result = ledgerService.projectedAnnualSpend(orgId);

        assertEquals(new BigDecimal("360.00"), result);
    }

    /** Returns zero when the organization has no properties. */
    @Test
    void returnsZeroWhenNoProperties() {
        when(propertyRepository.findByOrganizationId(orgId))
                .thenReturn(List.of());

        BigDecimal result = ledgerService.projectedAnnualSpend(orgId);

        assertEquals(BigDecimal.ZERO, result);
    }

    private MaintenanceSchedule schedule(Frequency frequency,
                                         Integer customIntervalDays,
                                         BigDecimal estimatedCost) {
        var s = new MaintenanceSchedule();
        s.setId(UUID.randomUUID());
        s.setPropertyId(propertyId);
        s.setDescription("Test schedule");
        s.setFrequency(frequency);
        s.setCustomIntervalDays(customIntervalDays);
        s.setNextDue(LocalDate.now().plusDays(30));
        s.setEstimatedCost(estimatedCost);
        return s;
    }

    private void stubOrgWithSchedules(List<MaintenanceSchedule> schedules) {
        when(propertyRepository.findByOrganizationId(orgId))
                .thenReturn(List.of(property));
        when(maintenanceScheduleRepository.findByPropertyId(propertyId))
                .thenReturn(schedules);
    }
}
