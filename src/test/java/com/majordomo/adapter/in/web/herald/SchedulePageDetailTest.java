package com.majordomo.adapter.in.web.herald;

import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.application.herald.ScheduleAccessGuard;
import com.majordomo.application.identity.CurrentOrganizationResolver;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.herald.Frequency;
import com.majordomo.domain.model.herald.MaintenanceSchedule;
import com.majordomo.domain.model.herald.ServiceRecord;
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
 * Slice tests for {@link SchedulePageController#detail} and
 * {@link SchedulePageController#addRecord}.
 */
@WebMvcTest(SchedulePageController.class)
@Import(SecurityConfig.class)
class SchedulePageDetailTest {

    @Autowired MockMvc mvc;

    @MockitoBean CurrentOrganizationResolver currentOrg;
    @MockitoBean ScheduleAccessGuard guard;
    @MockitoBean ManageScheduleUseCase scheduleUseCase;
    @MockitoBean MaintenanceScheduleRepository scheduleRepository;
    @MockitoBean PropertyRepository propertyRepository;
    @MockitoBean ApiKeyRepository apiKeyRepository;
    @MockitoBean OAuth2UserService oAuth2UserService;

    private static final UUID ORG_ID = UuidFactory.newId();
    private UUID scheduleId;
    private UUID propertyId;

    @BeforeEach
    void seedAuth() {
        User user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        when(currentOrg.resolve(any(UserDetails.class)))
                .thenReturn(new CurrentOrganizationResolver.Resolved(user, ORG_ID));
        scheduleId = UuidFactory.newId();
        propertyId = UuidFactory.newId();
    }

    /** GET renders schedule, property, and chronological records (newest first). */
    @Test
    @WithMockUser
    void detailRendersScheduleWithRecordsNewestFirst() throws Exception {
        Property property = property("Cabin", ORG_ID);
        property.setId(propertyId);
        MaintenanceSchedule schedule = schedule("HVAC service", propertyId,
                Frequency.ANNUAL, LocalDate.now().plusDays(45));
        schedule.setId(scheduleId);
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));
        when(scheduleUseCase.findRecordsByScheduleId(scheduleId)).thenReturn(List.of(
                record(scheduleId, LocalDate.of(2025, 6, 1), "First service", null),
                record(scheduleId, LocalDate.of(2026, 4, 1), "Most recent service",
                        new BigDecimal("199.99"))));

        MvcResult result = mvc.perform(get("/schedules/{id}", scheduleId))
                .andExpect(status().isOk())
                .andExpect(view().name("schedule-detail"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("HVAC service").contains("Cabin").contains("ANNUAL");
        // Newest first: "Most recent service" must appear before "First service".
        int newer = body.indexOf("Most recent service");
        int older = body.indexOf("First service");
        assertThat(newer).isPositive();
        assertThat(older).isGreaterThan(newer);
        assertThat(body).contains("$199.99");

        verify(guard).verifyForSchedule(scheduleId);
    }

    /** GET returns 404 when the schedule does not exist. */
    @Test
    @WithMockUser
    void detailReturns404WhenScheduleMissing() throws Exception {
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.empty());

        mvc.perform(get("/schedules/{id}", scheduleId))
                .andExpect(status().isNotFound());
    }

    /** GET renders empty-state when the schedule has no records yet. */
    @Test
    @WithMockUser
    void detailEmptyStateWhenNoRecords() throws Exception {
        MaintenanceSchedule schedule = schedule("New schedule", propertyId,
                Frequency.MONTHLY, LocalDate.now().plusDays(7));
        schedule.setId(scheduleId);
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.empty());
        when(scheduleUseCase.findRecordsByScheduleId(scheduleId)).thenReturn(List.of());

        mvc.perform(get("/schedules/{id}", scheduleId))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "No service events recorded yet")));
    }

    /** Cross-org access on the detail page returns 403 (guard throws). */
    @Test
    @WithMockUser
    void detailReturns403WhenAccessDenied() throws Exception {
        doThrow(new AccessDeniedException("denied")).when(guard).verifyForSchedule(scheduleId);

        mvc.perform(get("/schedules/{id}", scheduleId))
                .andExpect(status().isForbidden());

        verify(scheduleRepository, never()).findById(any());
    }

    /** POST records a service event, redirects to the detail page. */
    @Test
    @WithMockUser
    void addRecordRedirectsToDetailOnSuccess() throws Exception {
        when(scheduleUseCase.recordService(eq(scheduleId), any(ServiceRecord.class)))
                .thenAnswer(inv -> inv.getArgument(1));

        mvc.perform(post("/schedules/{id}/records", scheduleId)
                        .with(csrf())
                        .param("performedOn", "2026-04-29")
                        .param("description", "Replaced filter")
                        .param("cost", "29.99")
                        .param("notes", "Carbon-activated"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/schedules/" + scheduleId));

        ArgumentCaptor<ServiceRecord> captor = ArgumentCaptor.forClass(ServiceRecord.class);
        verify(scheduleUseCase).recordService(eq(scheduleId), captor.capture());
        ServiceRecord saved = captor.getValue();
        assertThat(saved.getPerformedOn()).isEqualTo(LocalDate.of(2026, 4, 29));
        assertThat(saved.getDescription()).isEqualTo("Replaced filter");
        assertThat(saved.getCost()).isEqualByComparingTo("29.99");
        assertThat(saved.getNotes()).isEqualTo("Carbon-activated");
        verify(guard).verifyForSchedule(scheduleId);
    }

    /** POST with blank description re-renders detail page with error and field state. */
    @Test
    @WithMockUser
    void addRecordRendersDetailWithErrorOnBlankDescription() throws Exception {
        seedDetailLookups();

        MvcResult result = mvc.perform(post("/schedules/{id}/records", scheduleId)
                        .with(csrf())
                        .param("performedOn", "2026-04-29")
                        .param("description", "")
                        .param("notes", "n/a"))
                .andExpect(status().isOk())
                .andExpect(view().name("schedule-detail"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Description is required.");
        // Field state echoed back.
        assertThat(body).contains("value=\"2026-04-29\"");
        assertThat(body).contains("value=\"n/a\"");
        verify(scheduleUseCase, never()).recordService(any(), any());
    }

    /** POST with missing performedOn re-renders with error. */
    @Test
    @WithMockUser
    void addRecordRendersDetailWithErrorOnMissingDate() throws Exception {
        seedDetailLookups();

        mvc.perform(post("/schedules/{id}/records", scheduleId)
                        .with(csrf())
                        .param("description", "no date"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Performed-on date is required.")));

        verify(scheduleUseCase, never()).recordService(any(), any());
    }

    /** POST is gated by the access guard. */
    @Test
    @WithMockUser
    void addRecordReturns403WhenAccessDenied() throws Exception {
        doThrow(new AccessDeniedException("denied")).when(guard).verifyForSchedule(scheduleId);

        mvc.perform(post("/schedules/{id}/records", scheduleId)
                        .with(csrf())
                        .param("performedOn", "2026-04-29")
                        .param("description", "x"))
                .andExpect(status().isForbidden());

        verify(scheduleUseCase, never()).recordService(any(), any());
    }

    private void seedDetailLookups() {
        MaintenanceSchedule schedule = schedule("HVAC", propertyId, Frequency.ANNUAL,
                LocalDate.now().plusDays(10));
        schedule.setId(scheduleId);
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.empty());
        when(scheduleUseCase.findRecordsByScheduleId(scheduleId)).thenReturn(List.of());
    }

    private static Property property(String name, UUID orgId) {
        Property p = new Property();
        p.setId(UuidFactory.newId());
        p.setOrganizationId(orgId);
        p.setName(name);
        return p;
    }

    private static MaintenanceSchedule schedule(String description, UUID propertyId,
                                                Frequency freq, LocalDate nextDue) {
        MaintenanceSchedule s = new MaintenanceSchedule();
        s.setId(UuidFactory.newId());
        s.setPropertyId(propertyId);
        s.setDescription(description);
        s.setFrequency(freq);
        s.setNextDue(nextDue);
        return s;
    }

    private static ServiceRecord record(UUID scheduleId, LocalDate performedOn,
                                        String description, BigDecimal cost) {
        ServiceRecord r = new ServiceRecord();
        r.setId(UuidFactory.newId());
        r.setScheduleId(scheduleId);
        r.setPerformedOn(performedOn);
        r.setDescription(description);
        r.setCost(cost);
        return r;
    }
}
