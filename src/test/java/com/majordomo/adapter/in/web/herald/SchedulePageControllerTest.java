package com.majordomo.adapter.in.web.herald;

import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.application.herald.ScheduleAccessGuard;
import com.majordomo.application.identity.CurrentOrganizationResolver;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.herald.Frequency;
import com.majordomo.domain.model.herald.MaintenanceSchedule;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.port.out.herald.MaintenanceScheduleRepository;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;
import com.majordomo.domain.port.out.steward.PropertyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Slice tests for {@link SchedulePageController}: list + filter at {@code /schedules}.
 */
@WebMvcTest(SchedulePageController.class)
@Import(SecurityConfig.class)
class SchedulePageControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean CurrentOrganizationResolver currentOrg;
    @MockitoBean ScheduleAccessGuard guard;
    @MockitoBean com.majordomo.domain.port.in.herald.ManageScheduleUseCase scheduleUseCase;
    @MockitoBean MaintenanceScheduleRepository scheduleRepository;
    @MockitoBean PropertyRepository propertyRepository;
    @MockitoBean ApiKeyRepository apiKeyRepository;
    @MockitoBean OAuth2UserService oAuth2UserService;

    private static final UUID ORG_ID = UuidFactory.newId();

    /** Renders schedules with property name + days-until-due, sorted soonest first. */
    @Test
    @WithMockUser
    void listRendersSchedulesWithPropertyName() throws Exception {
        seedAuth();
        Property p = property("Beach House", ORG_ID);
        when(propertyRepository.findByOrganizationId(ORG_ID)).thenReturn(List.of(p));
        when(scheduleRepository.findByPropertyId(p.getId())).thenReturn(List.of(
                schedule("HVAC service", p.getId(), Frequency.ANNUAL,
                        LocalDate.now().plusDays(30), null)));

        MvcResult result = mvc.perform(get("/schedules"))
                .andExpect(status().isOk())
                .andExpect(view().name("schedules"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("HVAC service");
        assertThat(body).contains("Beach House");
        assertThat(body).contains("ANNUAL");
        assertThat(body).contains("in 30 days");
    }

    /** Archived schedules are filtered out. */
    @Test
    @WithMockUser
    void listSkipsArchivedSchedules() throws Exception {
        seedAuth();
        Property p = property("Cabin", ORG_ID);
        when(propertyRepository.findByOrganizationId(ORG_ID)).thenReturn(List.of(p));
        when(scheduleRepository.findByPropertyId(p.getId())).thenReturn(List.of(
                schedule("Active", p.getId(), Frequency.QUARTERLY,
                        LocalDate.now().plusDays(7), null),
                schedule("Archived old", p.getId(), Frequency.ANNUAL,
                        LocalDate.now(), Instant.parse("2025-01-01T00:00:00Z"))));

        MvcResult result = mvc.perform(get("/schedules"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Active").doesNotContain("Archived old");
    }

    /** Frequency filter narrows the result set. */
    @Test
    @WithMockUser
    void filterByFrequencyNarrowsResults() throws Exception {
        seedAuth();
        Property p = property("Cabin", ORG_ID);
        when(propertyRepository.findByOrganizationId(ORG_ID)).thenReturn(List.of(p));
        when(scheduleRepository.findByPropertyId(p.getId())).thenReturn(List.of(
                schedule("Filter change", p.getId(), Frequency.QUARTERLY,
                        LocalDate.now().plusDays(7), null),
                schedule("Roof inspection", p.getId(), Frequency.ANNUAL,
                        LocalDate.now().plusDays(180), null)));

        MvcResult result = mvc.perform(get("/schedules").param("frequency", "QUARTERLY"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Filter change").doesNotContain("Roof inspection");
    }

    /** dueWithinDays filter narrows the result set. */
    @Test
    @WithMockUser
    void filterByDueWithinDaysNarrowsResults() throws Exception {
        seedAuth();
        Property p = property("Cabin", ORG_ID);
        when(propertyRepository.findByOrganizationId(ORG_ID)).thenReturn(List.of(p));
        when(scheduleRepository.findByPropertyId(p.getId())).thenReturn(List.of(
                schedule("Soon", p.getId(), Frequency.MONTHLY,
                        LocalDate.now().plusDays(5), null),
                schedule("Distant", p.getId(), Frequency.ANNUAL,
                        LocalDate.now().plusDays(200), null)));

        MvcResult result = mvc.perform(get("/schedules").param("dueWithinDays", "30"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Soon").doesNotContain("Distant");
    }

    /** Empty state renders when no schedules match. */
    @Test
    @WithMockUser
    void emptyStateRendersWhenNoSchedules() throws Exception {
        seedAuth();
        when(propertyRepository.findByOrganizationId(ORG_ID)).thenReturn(List.of());

        mvc.perform(get("/schedules"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "No schedules match")));
    }

    /** User with no organization redirects home. */
    @Test
    @WithMockUser
    void redirectsHomeWhenNoOrganization() throws Exception {
        User user = new User(UuidFactory.newId(), "lonely", "l@example.com");
        when(currentOrg.resolve(any(UserDetails.class)))
                .thenReturn(new CurrentOrganizationResolver.Resolved(user, null));

        mvc.perform(get("/schedules"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/"));
    }

    /** Unauthenticated request redirects to login. */
    @Test
    void unauthenticatedRedirectsToLogin() throws Exception {
        mvc.perform(get("/schedules"))
                .andExpect(status().is3xxRedirection());
    }

    private void seedAuth() {
        User user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        when(currentOrg.resolve(any(UserDetails.class)))
                .thenReturn(new CurrentOrganizationResolver.Resolved(user, ORG_ID));
        when(guard.currentUserOrganizationIds()).thenReturn(Set.of(ORG_ID));
    }

    private static Property property(String name, UUID orgId) {
        Property p = new Property();
        p.setId(UuidFactory.newId());
        p.setOrganizationId(orgId);
        p.setName(name);
        return p;
    }

    private static MaintenanceSchedule schedule(String description, UUID propertyId,
                                                Frequency freq, LocalDate nextDue,
                                                Instant archivedAt) {
        MaintenanceSchedule s = new MaintenanceSchedule();
        s.setId(UuidFactory.newId());
        s.setPropertyId(propertyId);
        s.setDescription(description);
        s.setFrequency(freq);
        s.setNextDue(nextDue);
        s.setArchivedAt(archivedAt);
        return s;
    }
}
