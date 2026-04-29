package com.majordomo.adapter.in.web;

import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.application.identity.OrganizationAccessService;
import com.majordomo.domain.model.DashboardSummary;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.port.in.DashboardUseCase;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link DashboardController} (REST). The Thymeleaf
 * {@code DashboardPageController} is covered separately.
 */
@WebMvcTest(DashboardController.class)
@Import(SecurityConfig.class)
class DashboardControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean DashboardUseCase dashboardUseCase;
    @MockitoBean OrganizationAccessService organizationAccessService;
    @MockitoBean ApiKeyRepository apiKeyRepository;
    @MockitoBean OAuth2UserService oAuth2UserService;

    private static final UUID ORG_ID = UuidFactory.newId();

    /** GET /api/dashboard returns the summary after access verification. */
    @Test
    @WithMockUser
    void getSummaryReturnsSummary() throws Exception {
        DashboardSummary summary = new DashboardSummary(
                3, 5, List.of(), List.of(), List.of(), new BigDecimal("1200.00"));
        when(dashboardUseCase.getSummary(ORG_ID)).thenReturn(summary);

        mvc.perform(get("/api/dashboard").param("organizationId", ORG_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propertyCount").value(3))
                .andExpect(jsonPath("$.contactCount").value(5))
                .andExpect(jsonPath("$.totalSpend").value(1200.00));

        verify(organizationAccessService).verifyAccess(ORG_ID);
    }

    /** Cross-org access returns 403. */
    @Test
    @WithMockUser
    void getSummaryReturns403WhenAccessDenied() throws Exception {
        doThrow(new AccessDeniedException("denied"))
                .when(organizationAccessService).verifyAccess(ORG_ID);

        mvc.perform(get("/api/dashboard").param("organizationId", ORG_ID.toString()))
                .andExpect(status().isForbidden());
    }
}
