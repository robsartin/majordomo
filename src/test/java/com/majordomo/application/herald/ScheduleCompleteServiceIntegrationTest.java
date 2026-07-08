package com.majordomo.application.herald;

import com.majordomo.IntegrationTest;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.herald.Frequency;
import com.majordomo.domain.model.herald.MaintenanceSchedule;
import com.majordomo.domain.model.identity.Organization;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.model.steward.PropertyStatus;
import com.majordomo.domain.port.in.herald.ManageScheduleUseCase;
import com.majordomo.domain.port.out.herald.MaintenanceScheduleRepository;
import com.majordomo.domain.port.out.herald.ServiceRecordRepository;
import com.majordomo.domain.port.out.identity.OrganizationRepository;
import com.majordomo.domain.port.out.steward.PropertyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end persistence for the dashboard "mark serviced" flow (#292): completing
 * a schedule records a service row and advances the schedule's next due date, both
 * durable in Postgres.
 */
@IntegrationTest
class ScheduleCompleteServiceIntegrationTest {

    @Autowired ManageScheduleUseCase schedules;
    @Autowired MaintenanceScheduleRepository scheduleRepository;
    @Autowired ServiceRecordRepository serviceRecords;
    @Autowired OrganizationRepository organizations;
    @Autowired PropertyRepository properties;

    @Test
    void completeServiceRecordsServiceAndAdvancesNextDue() {
        UUID orgId = UuidFactory.newId();
        organizations.save(new Organization(orgId, "org-" + orgId));

        Property furnace = new Property();
        furnace.setId(UuidFactory.newId());
        furnace.setOrganizationId(orgId);
        furnace.setName("Furnace");
        furnace.setStatus(PropertyStatus.ACTIVE);
        properties.save(furnace);

        MaintenanceSchedule schedule = new MaintenanceSchedule();
        schedule.setPropertyId(furnace.getId());
        schedule.setDescription("Replace HVAC filter");
        schedule.setFrequency(Frequency.MONTHLY);
        schedule.setNextDue(LocalDate.of(2026, 7, 15));
        MaintenanceSchedule created = schedules.create(schedule);

        schedules.completeService(created.getId(), LocalDate.of(2026, 7, 20));

        MaintenanceSchedule reloaded = scheduleRepository.findById(created.getId()).orElseThrow();
        assertThat(reloaded.getNextDue()).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(serviceRecords.findByScheduleId(created.getId()))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.getPerformedOn()).isEqualTo(LocalDate.of(2026, 7, 20));
                    assertThat(r.getPropertyId()).isEqualTo(furnace.getId());
                });
    }
}
