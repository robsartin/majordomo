package com.majordomo.adapter.in.web.herald;

import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.application.herald.ScheduleAccessGuard;
import com.majordomo.domain.port.in.herald.ManageScheduleUseCase;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Asserts that every {@link ScheduleController} endpoint runs through
 * {@link ScheduleAccessGuard} before delegating to the use case (issue #184).
 */
@WebMvcTest(ScheduleController.class)
@Import(SecurityConfig.class)
class ScheduleControllerAuthTest {

    @Autowired MockMvc mvc;

    @MockitoBean ManageScheduleUseCase scheduleUseCase;
    @MockitoBean ScheduleAccessGuard guard;
    @MockitoBean ApiKeyRepository apiKeyRepository;
    @MockitoBean OAuth2UserService oAuth2UserService;

    /** listByProperty calls verifyForProperty before delegating. */
    @Test
    @WithMockUser
    void listByPropertyVerifiesAccess() throws Exception {
        UUID propertyId = UUID.randomUUID();
        doThrow(new AccessDeniedException("denied")).when(guard).verifyForProperty(propertyId);

        mvc.perform(get("/api/schedules").param("propertyId", propertyId.toString()))
                .andExpect(status().isForbidden());

        verify(guard).verifyForProperty(propertyId);
        verify(scheduleUseCase, never()).findByPropertyId(any(), any(), any(Integer.class));
    }

    /** getById calls verifyForSchedule before delegating. */
    @Test
    @WithMockUser
    void getByIdVerifiesAccess() throws Exception {
        UUID scheduleId = UUID.randomUUID();
        doThrow(new AccessDeniedException("denied")).when(guard).verifyForSchedule(scheduleId);

        mvc.perform(get("/api/schedules/{id}", scheduleId))
                .andExpect(status().isForbidden());

        verify(guard).verifyForSchedule(scheduleId);
        verify(scheduleUseCase, never()).findById(any());
    }

    /** archive (DELETE) calls verifyForSchedule before delegating. */
    @Test
    @WithMockUser
    void archiveVerifiesAccess() throws Exception {
        UUID scheduleId = UUID.randomUUID();
        doThrow(new AccessDeniedException("denied")).when(guard).verifyForSchedule(scheduleId);

        mvc.perform(delete("/api/schedules/{id}", scheduleId).with(csrf()))
                .andExpect(status().isForbidden());

        verify(scheduleUseCase, never()).archive(any());
    }

    /** archiveRecord (nested DELETE) calls verifyForRecord before delegating. */
    @Test
    @WithMockUser
    void archiveRecordVerifiesAccess() throws Exception {
        UUID scheduleId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        doThrow(new AccessDeniedException("denied")).when(guard).verifyForRecord(recordId);

        mvc.perform(delete("/api/schedules/{id}/records/{recordId}", scheduleId, recordId).with(csrf()))
                .andExpect(status().isForbidden());

        verify(scheduleUseCase, never()).archiveRecord(any());
    }
}
