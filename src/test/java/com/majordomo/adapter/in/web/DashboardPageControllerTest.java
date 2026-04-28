package com.majordomo.adapter.in.web;

import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.application.identity.CurrentOrganizationResolver;
import com.majordomo.domain.model.DashboardSummary;
import com.majordomo.domain.model.envoy.ApplyNowPosting;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.port.in.DashboardUseCase;
import com.majordomo.domain.port.in.envoy.GetRecentApplyNowPostingsUseCase;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Tests for {@link DashboardPageController}.
 */
@WebMvcTest(DashboardPageController.class)
@Import(SecurityConfig.class)
class DashboardPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardUseCase dashboardUseCase;

    @MockitoBean
    private CurrentOrganizationResolver currentOrg;

    @MockitoBean
    private ApiKeyRepository apiKeyRepository;

    @MockitoBean
    private OAuth2UserService oAuth2UserService;

    @MockitoBean
    private GetRecentApplyNowPostingsUseCase recentApplyNowUseCase;

    /** Authenticated user with an organization membership sees the dashboard. */
    @Test
    @WithMockUser(username = "testuser")
    void dashboardReturns200ForAuthenticatedUser() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        User user = new User(userId, "testuser", "test@example.com");
        DashboardSummary summary = new DashboardSummary(
                3, 5, List.of(), List.of(), List.of(), new BigDecimal("1200.00"));

        ApplyNowPosting applyNow = new ApplyNowPosting(
                UUID.randomUUID(), UUID.randomUUID(),
                "Acme Inc", "Senior Engineer", "Remote", 92);

        when(currentOrg.resolve(any(UserDetails.class)))
                .thenReturn(new CurrentOrganizationResolver.Resolved(user, orgId));
        when(dashboardUseCase.getSummary(orgId)).thenReturn(summary);
        when(recentApplyNowUseCase.getRecentApplyNow(orgId, 5)).thenReturn(List.of(applyNow));

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attributeExists("summary"))
                .andExpect(model().attributeExists("username"))
                .andExpect(model().attributeExists("applyNowPostings"));
    }

    /** Unauthenticated access to dashboard redirects to login. */
    @Test
    void dashboardRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection());
    }

    /** User with no memberships is redirected to the home page. */
    @Test
    @WithMockUser(username = "nomember")
    void dashboardRedirectsWhenNoMembership() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "nomember", "nomember@example.com");

        when(currentOrg.resolve(any(UserDetails.class)))
                .thenReturn(new CurrentOrganizationResolver.Resolved(user, null));

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection());
    }
}
