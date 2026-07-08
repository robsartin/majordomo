package com.majordomo.adapter.in.web;

import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.application.herald.ScheduleAccessGuard;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.port.in.herald.ManageScheduleUseCase;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Slice tests for the dashboard "mark serviced" action (#292). */
@WebMvcTest(DashboardMaintenanceController.class)
@Import(SecurityConfig.class)
class DashboardMaintenanceControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean ManageScheduleUseCase scheduleUseCase;
    @MockitoBean ScheduleAccessGuard accessGuard;
    @MockitoBean OAuth2UserService oAuth2UserService;
    @MockitoBean ApiKeyRepository apiKeyRepository;

    @Test
    @WithMockUser
    void completeVerifiesAccessInvokesUseCaseAndRedirects() throws Exception {
        UUID scheduleId = UuidFactory.newId();

        mvc.perform(post("/dashboard/maintenance/{id}/complete", scheduleId).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));

        verify(accessGuard).verifyForSchedule(scheduleId);
        verify(scheduleUseCase).completeService(eq(scheduleId), any(LocalDate.class));
    }
}
