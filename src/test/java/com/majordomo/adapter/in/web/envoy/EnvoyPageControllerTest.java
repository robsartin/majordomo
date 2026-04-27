package com.majordomo.adapter.in.web.envoy;

import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.model.identity.Membership;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.port.in.envoy.QueryScoreReportsUseCase;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;
import com.majordomo.domain.port.out.identity.MembershipRepository;
import com.majordomo.domain.port.out.identity.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(EnvoyPageController.class)
@Import(SecurityConfig.class)
class EnvoyPageControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean QueryScoreReportsUseCase reports;
    @MockitoBean UserRepository userRepository;
    @MockitoBean MembershipRepository membershipRepository;
    @MockitoBean ApiKeyRepository apiKeyRepository;
    @MockitoBean OAuth2UserService oAuth2UserService;

    private static final UUID ORG_ID = UuidFactory.newId();

    @Test
    @WithMockUser(username = "robsartin")
    void rendersEnvoyPageWithReports() throws Exception {
        var user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        var membership = new Membership();
        membership.setUserId(user.getId());
        membership.setOrganizationId(ORG_ID);
        var report = new ScoreReport(UuidFactory.newId(), ORG_ID, UuidFactory.newId(),
                UuidFactory.newId(), 1, Optional.empty(),
                List.of(), List.of(), 87, 87, Recommendation.APPLY_NOW,
                "claude-sonnet-4-6", Instant.now());

        when(userRepository.findByUsername("robsartin")).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserId(user.getId())).thenReturn(List.of(membership));
        when(reports.query(eq(ORG_ID), any(), any(), any(), any(Integer.class)))
                .thenReturn(new Page<>(List.of(report), null, false));

        mvc.perform(get("/envoy"))
                .andExpect(status().isOk())
                .andExpect(view().name("envoy"))
                .andExpect(model().attribute("reports", List.of(report)))
                .andExpect(model().attribute("organizationId", ORG_ID))
                .andExpect(model().attribute("username", "robsartin"));
    }

    @Test
    @WithMockUser(username = "robsartin")
    void redirectsHomeWhenUserHasNoMembership() throws Exception {
        var user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        when(userRepository.findByUsername("robsartin")).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserId(user.getId())).thenReturn(List.of());

        mvc.perform(get("/envoy"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/"));
    }

    @Test
    void unauthenticatedRedirectsToLogin() throws Exception {
        mvc.perform(get("/envoy"))
                .andExpect(status().is3xxRedirection());
    }
}
