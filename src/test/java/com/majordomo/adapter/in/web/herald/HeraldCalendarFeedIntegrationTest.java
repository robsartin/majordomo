package com.majordomo.adapter.in.web.herald;

import com.majordomo.IntegrationTest;
import com.majordomo.application.herald.CalendarTokenService;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.herald.Frequency;
import com.majordomo.domain.model.herald.MaintenanceSchedule;
import com.majordomo.domain.model.identity.Organization;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.model.steward.PropertyStatus;
import com.majordomo.domain.port.out.herald.MaintenanceScheduleRepository;
import com.majordomo.domain.port.out.identity.OrganizationRepository;
import com.majordomo.domain.port.out.identity.UserRepository;
import com.majordomo.domain.port.out.steward.PropertyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end proof of the iCalendar feed (#286): an anonymous request bearing a
 * valid token returns a VCALENDAR of the org's upcoming maintenance + warranty
 * events; an unknown/revoked token returns 404. Runs against Testcontainers
 * Postgres so the org-scoped join query is exercised for real.
 */
@IntegrationTest
@AutoConfigureMockMvc
class HeraldCalendarFeedIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired OrganizationRepository organizations;
    @Autowired UserRepository users;
    @Autowired PropertyRepository properties;
    @Autowired MaintenanceScheduleRepository schedules;
    @Autowired CalendarTokenService tokens;

    private record Fixture(UUID userId, UUID orgId) { }

    private Fixture seed() {
        UUID orgId = UuidFactory.newId();
        organizations.save(new Organization(orgId, "org-" + orgId));
        UUID userId = UuidFactory.newId();
        users.save(new User(userId, "u-" + userId, userId + "@example.com"));

        Property furnace = new Property();
        furnace.setId(UuidFactory.newId());
        furnace.setOrganizationId(orgId);
        furnace.setName("Furnace");
        furnace.setStatus(PropertyStatus.ACTIVE);
        furnace.setWarrantyExpiresOn(LocalDate.of(2027, 9, 1));
        properties.save(furnace);

        MaintenanceSchedule filter = new MaintenanceSchedule();
        filter.setId(UuidFactory.newId());
        filter.setPropertyId(furnace.getId());
        filter.setDescription("Replace HVAC filter");
        filter.setFrequency(Frequency.MONTHLY);
        filter.setNextDue(LocalDate.of(2027, 7, 15));
        schedules.save(filter);

        return new Fixture(userId, orgId);
    }

    @Test
    void validTokenReturnsCalendarFeed() throws Exception {
        Fixture f = seed();
        String raw = tokens.issue(f.userId(), f.orgId());

        mvc.perform(get("/herald/calendar/{token}.ics", raw))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/calendar"))
                .andExpect(content().string(containsString("BEGIN:VCALENDAR")))
                .andExpect(content().string(containsString("Replace HVAC filter")))
                .andExpect(content().string(containsString("Warranty expires: Furnace")));
    }

    @Test
    void unknownTokenReturnsNotFound() throws Exception {
        mvc.perform(get("/herald/calendar/{token}.ics", "deadbeefdeadbeef"))
                .andExpect(status().isNotFound());
    }

    @Test
    void revokedTokenReturnsNotFound() throws Exception {
        Fixture f = seed();
        String raw = tokens.issue(f.userId(), f.orgId());
        var token = tokens.resolve(raw).orElseThrow();
        tokens.revoke(token.getId(), f.userId());

        mvc.perform(get("/herald/calendar/{token}.ics", raw))
                .andExpect(status().isNotFound());
    }
}
