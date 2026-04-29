package com.majordomo.adapter.in.web.steward;

import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.application.identity.CurrentOrganizationResolver;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.herald.Frequency;
import com.majordomo.domain.model.herald.MaintenanceSchedule;
import com.majordomo.domain.model.herald.ServiceRecord;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.port.in.ManageAttachmentUseCase;
import com.majordomo.domain.port.in.concierge.ManageContactUseCase;
import com.majordomo.domain.port.in.herald.ManageScheduleUseCase;
import com.majordomo.domain.port.in.steward.ManagePropertyUseCase;
import com.majordomo.domain.port.out.herald.ServiceRecordRepository;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;
import com.majordomo.domain.port.out.identity.MembershipRepository;
import com.majordomo.domain.port.out.identity.UserRepository;
import com.majordomo.domain.port.out.steward.PropertyContactRepository;
import com.majordomo.domain.port.out.steward.PropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for the new "Maintenance schedules" + "Recent service records"
 * panels added to the property-detail page in #224.
 */
@WebMvcTest(PropertyPageController.class)
@Import(SecurityConfig.class)
class PropertyPageDetailPanelsTest {

    @Autowired MockMvc mvc;

    @MockitoBean ManagePropertyUseCase propertyUseCase;
    @MockitoBean ManageScheduleUseCase scheduleUseCase;
    @MockitoBean ManageContactUseCase contactUseCase;
    @MockitoBean ManageAttachmentUseCase attachmentUseCase;
    @MockitoBean PropertyContactRepository propertyContactRepository;
    @MockitoBean PropertyRepository propertyRepository;
    @MockitoBean ServiceRecordRepository serviceRecordRepository;
    @MockitoBean CurrentOrganizationResolver currentOrg;
    @MockitoBean com.majordomo.application.identity.OrganizationAccessService organizationAccessService;
    @MockitoBean UserRepository userRepository;
    @MockitoBean MembershipRepository membershipRepository;
    @MockitoBean ApiKeyRepository apiKeyRepository;
    @MockitoBean OAuth2UserService oAuth2UserService;

    private UUID propertyId;
    private static final UUID ORG_ID = UuidFactory.newId();

    @BeforeEach
    void seed() {
        propertyId = UuidFactory.newId();
        Property property = new Property();
        property.setId(propertyId);
        property.setOrganizationId(ORG_ID);
        property.setName("Cabin");
        when(propertyUseCase.findById(propertyId)).thenReturn(Optional.of(property));
        when(propertyUseCase.findByParentId(propertyId)).thenReturn(List.of());
        when(propertyContactRepository.findByPropertyId(propertyId)).thenReturn(List.of());
        when(attachmentUseCase.list("property", propertyId)).thenReturn(List.of());
        when(userRepository.findByUsername("robsartin")).thenReturn(Optional.of(
                new User(UuidFactory.newId(), "robsartin", "rob@example.com")));
    }

    /** Schedules panel renders rows with a link to /schedules/{id} and an "Add schedule" button. */
    @Test
    @WithMockUser(username = "robsartin")
    void schedulesPanelRendersRowsAndAddButton() throws Exception {
        UUID scheduleId = UuidFactory.newId();
        MaintenanceSchedule s = new MaintenanceSchedule();
        s.setId(scheduleId);
        s.setPropertyId(propertyId);
        s.setDescription("HVAC service");
        s.setFrequency(Frequency.ANNUAL);
        s.setNextDue(LocalDate.now().plusDays(30));
        when(scheduleUseCase.findByPropertyId(propertyId)).thenReturn(List.of(s));
        when(serviceRecordRepository.findByPropertyId(propertyId)).thenReturn(List.of());

        MvcResult result = mvc.perform(get("/properties/{id}", propertyId))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("HVAC service").contains("ANNUAL");
        assertThat(body).contains("/schedules/" + scheduleId);
        assertThat(body).contains("/schedules/new?propertyId=" + propertyId);
        assertThat(body).contains("in 30 days");
    }

    /** Schedules panel skips archived schedules. */
    @Test
    @WithMockUser(username = "robsartin")
    void schedulesPanelSkipsArchivedSchedules() throws Exception {
        MaintenanceSchedule active = scheduleNamed("Active filter change");
        active.setNextDue(LocalDate.now().plusDays(7));
        MaintenanceSchedule archived = scheduleNamed("Archived old");
        archived.setNextDue(LocalDate.now());
        archived.setArchivedAt(Instant.parse("2025-01-01T00:00:00Z"));
        when(scheduleUseCase.findByPropertyId(propertyId)).thenReturn(List.of(active, archived));
        when(serviceRecordRepository.findByPropertyId(propertyId)).thenReturn(List.of());

        MvcResult result = mvc.perform(get("/properties/{id}", propertyId))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Active filter change").doesNotContain("Archived old");
    }

    /** Recent records panel renders rows newest-first. */
    @Test
    @WithMockUser(username = "robsartin")
    void recentRecordsPanelRendersNewestFirst() throws Exception {
        when(scheduleUseCase.findByPropertyId(propertyId)).thenReturn(List.of());
        when(serviceRecordRepository.findByPropertyId(propertyId)).thenReturn(List.of(
                record(LocalDate.of(2025, 6, 1), "First service", null),
                record(LocalDate.of(2026, 4, 1), "Most recent service",
                        new BigDecimal("199.99"))));

        MvcResult result = mvc.perform(get("/properties/{id}", propertyId))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        int newer = body.indexOf("Most recent service");
        int older = body.indexOf("First service");
        assertThat(newer).isPositive();
        assertThat(older).isGreaterThan(newer);
        assertThat(body).contains("$199.99");
    }

    /** Recent records panel renders empty state when no records exist. */
    @Test
    @WithMockUser(username = "robsartin")
    void recentRecordsPanelRendersEmptyState() throws Exception {
        when(scheduleUseCase.findByPropertyId(propertyId)).thenReturn(List.of());
        when(serviceRecordRepository.findByPropertyId(propertyId)).thenReturn(List.of());

        MvcResult result = mvc.perform(get("/properties/{id}", propertyId))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .contains("No service records yet for this property.");
    }

    private MaintenanceSchedule scheduleNamed(String description) {
        MaintenanceSchedule s = new MaintenanceSchedule();
        s.setId(UuidFactory.newId());
        s.setPropertyId(propertyId);
        s.setDescription(description);
        s.setFrequency(Frequency.MONTHLY);
        return s;
    }

    private ServiceRecord record(LocalDate performedOn, String description, BigDecimal cost) {
        ServiceRecord r = new ServiceRecord();
        r.setId(UuidFactory.newId());
        r.setPropertyId(propertyId);
        r.setPerformedOn(performedOn);
        r.setDescription(description);
        r.setCost(cost);
        return r;
    }
}
