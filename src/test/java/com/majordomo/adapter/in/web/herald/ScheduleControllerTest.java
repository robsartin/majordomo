package com.majordomo.adapter.in.web.herald;

import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.application.herald.ScheduleAccessGuard;
import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.herald.Frequency;
import com.majordomo.domain.model.herald.MaintenanceSchedule;
import com.majordomo.domain.model.herald.ServiceRecord;
import com.majordomo.domain.port.in.herald.ManageScheduleUseCase;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Golden-path slice tests for {@link ScheduleController}. Auth gating is
 * covered by {@link ScheduleControllerAuthTest}; this test suite mocks the
 * guard's verify methods as no-ops and asserts each endpoint's wiring,
 * delegation, and response shape.
 */
@WebMvcTest(ScheduleController.class)
@Import(SecurityConfig.class)
class ScheduleControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean ManageScheduleUseCase scheduleUseCase;
    @MockitoBean ScheduleAccessGuard guard;
    @MockitoBean ApiKeyRepository apiKeyRepository;
    @MockitoBean OAuth2UserService oAuth2UserService;

    private static final UUID PROPERTY_ID = UuidFactory.newId();

    /** GET /api/schedules without `q` delegates to findByPropertyId. */
    @Test
    @WithMockUser
    void listByPropertyDelegatesToFindByPropertyId() throws Exception {
        MaintenanceSchedule schedule = sampleSchedule(PROPERTY_ID);
        when(scheduleUseCase.findByPropertyId(eq(PROPERTY_ID), any(), any(Integer.class)))
                .thenReturn(new Page<>(List.of(schedule), null, false));

        mvc.perform(get("/api/schedules")
                        .param("propertyId", PROPERTY_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(schedule.getId().toString()));

        verify(guard).verifyForProperty(PROPERTY_ID);
        verify(scheduleUseCase).findByPropertyId(eq(PROPERTY_ID), any(), any(Integer.class));
    }

    /** GET /api/schedules with `q` delegates to search. */
    @Test
    @WithMockUser
    void listByPropertyDelegatesToSearchWhenQuerySupplied() throws Exception {
        when(scheduleUseCase.search(eq(PROPERTY_ID), eq("hvac"), eq("ANNUAL"),
                any(), any(Integer.class)))
                .thenReturn(new Page<>(List.of(), null, false));

        mvc.perform(get("/api/schedules")
                        .param("propertyId", PROPERTY_ID.toString())
                        .param("q", "hvac")
                        .param("frequency", "ANNUAL"))
                .andExpect(status().isOk());

        verify(scheduleUseCase).search(eq(PROPERTY_ID), eq("hvac"), eq("ANNUAL"),
                any(), any(Integer.class));
    }

    /** GET /api/schedules/upcoming filters results to the current user's orgs. */
    @Test
    @WithMockUser
    void listUpcomingFiltersToCurrentUser() throws Exception {
        MaintenanceSchedule schedule = sampleSchedule(PROPERTY_ID);
        when(scheduleUseCase.findDueBefore(any(LocalDate.class)))
                .thenReturn(List.of(schedule));
        when(guard.filterToCurrentUser(List.of(schedule))).thenReturn(List.of(schedule));

        mvc.perform(get("/api/schedules/upcoming"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(schedule.getId().toString()));

        verify(guard).filterToCurrentUser(List.of(schedule));
    }

    /** GET /api/schedules/{id} returns the schedule from the use case. */
    @Test
    @WithMockUser
    void getByIdReturnsSchedule() throws Exception {
        MaintenanceSchedule schedule = sampleSchedule(PROPERTY_ID);
        when(scheduleUseCase.findById(schedule.getId())).thenReturn(Optional.of(schedule));

        mvc.perform(get("/api/schedules/{id}", schedule.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(schedule.getId().toString()));

        verify(guard).verifyForSchedule(schedule.getId());
    }

    /** GET /api/schedules/{id} returns 404 when no match. */
    @Test
    @WithMockUser
    void getByIdReturns404WhenMissing() throws Exception {
        UUID id = UuidFactory.newId();
        when(scheduleUseCase.findById(id)).thenReturn(Optional.empty());

        mvc.perform(get("/api/schedules/{id}", id))
                .andExpect(status().isNotFound());
    }

    /** POST /api/schedules creates a schedule and returns 201 with Location. */
    @Test
    @WithMockUser
    void createReturns201WithLocation() throws Exception {
        UUID newId = UuidFactory.newId();
        MaintenanceSchedule saved = sampleSchedule(PROPERTY_ID);
        saved.setId(newId);
        when(scheduleUseCase.create(any(MaintenanceSchedule.class))).thenReturn(saved);

        String body = """
                {
                  "propertyId": "%s",
                  "description": "HVAC service",
                  "frequency": "ANNUAL",
                  "nextDue": "2026-06-01"
                }
                """.formatted(PROPERTY_ID);

        mvc.perform(post("/api/schedules")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/schedules/" + newId));

        verify(guard).verifyForProperty(PROPERTY_ID);
    }

    /** POST /api/schedules/{id}/records records a service event with 201 + Location. */
    @Test
    @WithMockUser
    void recordServiceReturns201WithLocation() throws Exception {
        UUID scheduleId = UuidFactory.newId();
        ServiceRecord saved = sampleRecord(PROPERTY_ID, scheduleId);
        when(scheduleUseCase.recordService(eq(scheduleId), any(ServiceRecord.class)))
                .thenReturn(saved);

        String body = """
                {
                  "propertyId": "%s",
                  "performedOn": "2026-04-15",
                  "description": "Replaced filter"
                }
                """.formatted(PROPERTY_ID);

        mvc.perform(post("/api/schedules/{id}/records", scheduleId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        "/api/schedules/" + scheduleId + "/records/" + saved.getId()));

        verify(guard).verifyForSchedule(scheduleId);
    }

    /** GET /api/schedules/{id}/records returns the records for that schedule. */
    @Test
    @WithMockUser
    void listRecordsReturnsAll() throws Exception {
        UUID scheduleId = UuidFactory.newId();
        ServiceRecord record = sampleRecord(PROPERTY_ID, scheduleId);
        when(scheduleUseCase.findRecordsByScheduleId(scheduleId)).thenReturn(List.of(record));

        mvc.perform(get("/api/schedules/{id}/records", scheduleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(record.getId().toString()));

        verify(guard).verifyForSchedule(scheduleId);
    }

    /** PUT /api/schedules/{id} updates and returns the updated schedule. */
    @Test
    @WithMockUser
    void updateReturnsUpdatedSchedule() throws Exception {
        UUID id = UuidFactory.newId();
        MaintenanceSchedule updated = sampleSchedule(PROPERTY_ID);
        updated.setId(id);
        updated.setDescription("Quarterly HVAC inspection");
        when(scheduleUseCase.update(eq(id), any(MaintenanceSchedule.class))).thenReturn(updated);

        String body = """
                {
                  "propertyId": "%s",
                  "description": "Quarterly HVAC inspection",
                  "frequency": "QUARTERLY",
                  "nextDue": "2026-07-01"
                }
                """.formatted(PROPERTY_ID);

        mvc.perform(put("/api/schedules/{id}", id)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.description").value("Quarterly HVAC inspection"));

        verify(guard).verifyForSchedule(id);
    }

    /** DELETE /api/schedules/{id} archives the schedule and returns 204. */
    @Test
    @WithMockUser
    void archiveReturns204() throws Exception {
        UUID id = UuidFactory.newId();

        mvc.perform(delete("/api/schedules/{id}", id).with(csrf()))
                .andExpect(status().isNoContent());

        verify(guard).verifyForSchedule(id);
        verify(scheduleUseCase).archive(id);
    }

    /** PUT /api/schedules/{id}/records/{recordId} updates the record. */
    @Test
    @WithMockUser
    void updateRecordReturnsUpdated() throws Exception {
        UUID scheduleId = UuidFactory.newId();
        UUID recordId = UuidFactory.newId();
        ServiceRecord updated = sampleRecord(PROPERTY_ID, scheduleId);
        updated.setId(recordId);
        when(scheduleUseCase.updateRecord(eq(recordId), any(ServiceRecord.class))).thenReturn(updated);

        String body = """
                {
                  "propertyId": "%s",
                  "performedOn": "2026-04-20",
                  "description": "Re-inspection"
                }
                """.formatted(PROPERTY_ID);

        mvc.perform(put("/api/schedules/{id}/records/{recordId}", scheduleId, recordId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(recordId.toString()));

        verify(guard).verifyForRecord(recordId);
    }

    /** DELETE /api/schedules/{id}/records/{recordId} archives the record. */
    @Test
    @WithMockUser
    void archiveRecordReturns204() throws Exception {
        UUID scheduleId = UuidFactory.newId();
        UUID recordId = UuidFactory.newId();

        mvc.perform(delete("/api/schedules/{id}/records/{recordId}", scheduleId, recordId)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(guard).verifyForRecord(recordId);
        verify(scheduleUseCase).archiveRecord(recordId);
    }

    private static MaintenanceSchedule sampleSchedule(UUID propertyId) {
        MaintenanceSchedule s = new MaintenanceSchedule();
        s.setId(UuidFactory.newId());
        s.setPropertyId(propertyId);
        s.setDescription("HVAC inspection");
        s.setFrequency(Frequency.ANNUAL);
        s.setNextDue(LocalDate.of(2026, 6, 1));
        return s;
    }

    private static ServiceRecord sampleRecord(UUID propertyId, UUID scheduleId) {
        ServiceRecord r = new ServiceRecord();
        r.setId(UuidFactory.newId());
        r.setPropertyId(propertyId);
        r.setScheduleId(scheduleId);
        r.setPerformedOn(LocalDate.of(2026, 4, 15));
        r.setDescription("Replaced filter");
        return r;
    }
}
