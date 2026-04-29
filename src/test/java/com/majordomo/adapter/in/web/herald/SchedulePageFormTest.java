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
import com.majordomo.domain.port.in.herald.ManageScheduleUseCase;
import com.majordomo.domain.port.out.herald.MaintenanceScheduleRepository;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;
import com.majordomo.domain.port.out.steward.PropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Slice tests for the schedule add/edit forms exposed by
 * {@link SchedulePageController}.
 */
@WebMvcTest(SchedulePageController.class)
@Import(SecurityConfig.class)
class SchedulePageFormTest {

    @Autowired MockMvc mvc;

    @MockitoBean CurrentOrganizationResolver currentOrg;
    @MockitoBean ScheduleAccessGuard guard;
    @MockitoBean ManageScheduleUseCase scheduleUseCase;
    @MockitoBean MaintenanceScheduleRepository scheduleRepository;
    @MockitoBean PropertyRepository propertyRepository;
    @MockitoBean ApiKeyRepository apiKeyRepository;
    @MockitoBean OAuth2UserService oAuth2UserService;

    private static final UUID ORG_ID = UuidFactory.newId();
    private UUID propertyId;
    private UUID scheduleId;

    @BeforeEach
    void seedAuth() {
        User user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        when(currentOrg.resolve(any(UserDetails.class)))
                .thenReturn(new CurrentOrganizationResolver.Resolved(user, ORG_ID));
        when(guard.currentUserOrganizationIds()).thenReturn(Set.of(ORG_ID));
        propertyId = UuidFactory.newId();
        scheduleId = UuidFactory.newId();
        when(propertyRepository.findByOrganizationId(ORG_ID)).thenReturn(List.of(
                property(propertyId, "Cabin")));
    }

    /** GET /schedules/new renders the form with property options. */
    @Test
    @WithMockUser
    void newFormRendersWithPropertyOptions() throws Exception {
        MvcResult result = mvc.perform(get("/schedules/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("schedule-form"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("New schedule").contains("Cabin");
        assertThat(body).contains("ANNUAL").contains("WEEKLY");
    }

    /** GET /schedules/new?propertyId=X pre-selects that property in the picker. */
    @Test
    @WithMockUser
    void newFormPreSelectsPropertyFromQueryParam() throws Exception {
        MvcResult result = mvc.perform(get("/schedules/new")
                        .param("propertyId", propertyId.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("schedule-form"))
                .andReturn();

        // Find the option tag bearing the propertyId; it must carry "selected".
        String body = result.getResponse().getContentAsString();
        int idx = body.indexOf("value=\"" + propertyId + "\"");
        assertThat(idx).isPositive();
        int closeTag = body.indexOf(">", idx);
        assertThat(body.substring(idx, closeTag)).contains("selected");
    }

    /** GET /schedules/{id}/edit pre-populates the form fields. */
    @Test
    @WithMockUser
    void editFormPrePopulates() throws Exception {
        MaintenanceSchedule existing = schedule("HVAC service", propertyId,
                Frequency.QUARTERLY, LocalDate.of(2026, 7, 1));
        existing.setId(scheduleId);
        existing.setEstimatedCost(new BigDecimal("125.00"));
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(existing));

        MvcResult result = mvc.perform(get("/schedules/{id}/edit", scheduleId))
                .andExpect(status().isOk())
                .andExpect(view().name("schedule-form"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Edit schedule");
        assertThat(body).contains("value=\"HVAC service\"");
        assertThat(body).contains("value=\"2026-07-01\"");
        assertThat(body).contains("value=\"125.00\"");
        verify(guard).verifyForSchedule(scheduleId);
    }

    /** GET /schedules/{id}/edit returns 404 when missing. */
    @Test
    @WithMockUser
    void editFormReturns404WhenMissing() throws Exception {
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.empty());

        mvc.perform(get("/schedules/{id}/edit", scheduleId))
                .andExpect(status().isNotFound());
    }

    /** POST /schedules creates and redirects to the new detail page. */
    @Test
    @WithMockUser
    void createRedirectsToDetailPage() throws Exception {
        MaintenanceSchedule saved = schedule("HVAC service", propertyId,
                Frequency.ANNUAL, LocalDate.of(2026, 6, 1));
        UUID newId = UuidFactory.newId();
        saved.setId(newId);
        when(scheduleUseCase.create(any(MaintenanceSchedule.class))).thenReturn(saved);

        mvc.perform(post("/schedules")
                        .with(csrf())
                        .param("propertyId", propertyId.toString())
                        .param("description", "HVAC service")
                        .param("frequency", "ANNUAL")
                        .param("nextDue", "2026-06-01")
                        .param("estimatedCost", "199.99"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/schedules/" + newId));

        ArgumentCaptor<MaintenanceSchedule> captor = ArgumentCaptor.forClass(MaintenanceSchedule.class);
        verify(scheduleUseCase).create(captor.capture());
        MaintenanceSchedule submitted = captor.getValue();
        assertThat(submitted.getDescription()).isEqualTo("HVAC service");
        assertThat(submitted.getFrequency()).isEqualTo(Frequency.ANNUAL);
        assertThat(submitted.getNextDue()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(submitted.getEstimatedCost()).isEqualByComparingTo("199.99");
        verify(guard).verifyForProperty(propertyId);
    }

    /** POST /schedules with blank description re-renders the form with state preserved. */
    @Test
    @WithMockUser
    void createRendersFormOnValidationError() throws Exception {
        MvcResult result = mvc.perform(post("/schedules")
                        .with(csrf())
                        .param("propertyId", propertyId.toString())
                        .param("description", "")
                        .param("frequency", "ANNUAL")
                        .param("nextDue", "2026-06-01"))
                .andExpect(status().isOk())
                .andExpect(view().name("schedule-form"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Description is required.");
        // Form state echoed.
        assertThat(body).contains("value=\"2026-06-01\"");
        verify(scheduleUseCase, never()).create(any());
    }

    /** POST /schedules with no propertyId re-renders form with error. */
    @Test
    @WithMockUser
    void createRendersFormWhenPropertyMissing() throws Exception {
        mvc.perform(post("/schedules")
                        .with(csrf())
                        .param("description", "x")
                        .param("frequency", "ANNUAL")
                        .param("nextDue", "2026-06-01"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Property is required.")));

        verify(guard, never()).verifyForProperty(any());
    }

    /** POST /schedules returns 403 when the user can't access the property. */
    @Test
    @WithMockUser
    void createReturns403WhenPropertyAccessDenied() throws Exception {
        doThrow(new AccessDeniedException("denied"))
                .when(guard).verifyForProperty(propertyId);

        mvc.perform(post("/schedules")
                        .with(csrf())
                        .param("propertyId", propertyId.toString())
                        .param("description", "HVAC")
                        .param("frequency", "ANNUAL")
                        .param("nextDue", "2026-06-01"))
                .andExpect(status().isForbidden());

        verify(scheduleUseCase, never()).create(any());
    }

    /** POST /schedules/{id} updates and redirects to the detail page. */
    @Test
    @WithMockUser
    void updateRedirectsToDetailPage() throws Exception {
        MaintenanceSchedule existing = schedule("Old", propertyId,
                Frequency.QUARTERLY, LocalDate.of(2026, 1, 1));
        existing.setId(scheduleId);
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(existing));
        MaintenanceSchedule updated = schedule("New", propertyId,
                Frequency.ANNUAL, LocalDate.of(2026, 7, 1));
        updated.setId(scheduleId);
        when(scheduleUseCase.update(eq(scheduleId), any(MaintenanceSchedule.class)))
                .thenReturn(updated);

        mvc.perform(post("/schedules/{id}", scheduleId)
                        .with(csrf())
                        .param("propertyId", propertyId.toString())
                        .param("description", "New")
                        .param("frequency", "ANNUAL")
                        .param("nextDue", "2026-07-01"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/schedules/" + scheduleId));

        verify(guard).verifyForSchedule(scheduleId);
    }

    /** POST /schedules/{id} re-checks property access when the property changes. */
    @Test
    @WithMockUser
    void updateRevalidatesPropertyWhenChanged() throws Exception {
        MaintenanceSchedule existing = schedule("HVAC", propertyId,
                Frequency.ANNUAL, LocalDate.of(2026, 1, 1));
        existing.setId(scheduleId);
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(existing));

        UUID newPropertyId = UuidFactory.newId();
        when(scheduleUseCase.update(any(), any())).thenReturn(existing);

        mvc.perform(post("/schedules/{id}", scheduleId)
                        .with(csrf())
                        .param("propertyId", newPropertyId.toString())
                        .param("description", "HVAC")
                        .param("frequency", "ANNUAL")
                        .param("nextDue", "2026-07-01"))
                .andExpect(status().is3xxRedirection());

        verify(guard).verifyForSchedule(scheduleId);
        verify(guard).verifyForProperty(newPropertyId);
    }

    /** POST /schedules/{id} with bad inputs re-renders form. */
    @Test
    @WithMockUser
    void updateRendersFormOnValidationError() throws Exception {
        MaintenanceSchedule existing = schedule("HVAC", propertyId,
                Frequency.ANNUAL, LocalDate.of(2026, 1, 1));
        existing.setId(scheduleId);
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(existing));

        mvc.perform(post("/schedules/{id}", scheduleId)
                        .with(csrf())
                        .param("propertyId", propertyId.toString())
                        .param("description", "valid")
                        .param("frequency", "ANNUAL")
                        .param("nextDue", "not-a-date"))
                .andExpect(status().isOk())
                .andExpect(view().name("schedule-form"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Next due must be a valid date")));

        verify(scheduleUseCase, never()).update(any(), any());
    }

    private static Property property(UUID id, String name) {
        Property p = new Property();
        p.setId(id);
        p.setOrganizationId(ORG_ID);
        p.setName(name);
        return p;
    }

    private static MaintenanceSchedule schedule(String desc, UUID propertyId,
                                                Frequency freq, LocalDate nextDue) {
        MaintenanceSchedule s = new MaintenanceSchedule();
        s.setPropertyId(propertyId);
        s.setDescription(desc);
        s.setFrequency(freq);
        s.setNextDue(nextDue);
        return s;
    }
}
