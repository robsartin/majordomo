package com.majordomo.adapter.in.web.envoy;

import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.identity.Membership;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.model.envoy.ApplyNowConversionStat;
import com.majordomo.domain.port.in.envoy.GetApplyNowConversionStatUseCase;
import com.majordomo.domain.port.in.envoy.IngestJobPostingUseCase;
import com.majordomo.domain.port.in.envoy.MarkPostingConversionUseCase;
import com.majordomo.domain.port.in.envoy.QueryScoreReportsUseCase;
import com.majordomo.domain.port.in.envoy.ScoreJobPostingUseCase;
import com.majordomo.domain.port.out.envoy.JobPostingRepository;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;
import com.majordomo.application.identity.CurrentOrganizationResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Conversion-action endpoints on {@link EnvoyPageController}: mark applied / dismissed.
 */
@WebMvcTest(EnvoyPageController.class)
@Import(SecurityConfig.class)
class EnvoyPageControllerConversionTest {

    @Autowired MockMvc mvc;

    @MockitoBean QueryScoreReportsUseCase reports;
    @MockitoBean IngestJobPostingUseCase ingestUseCase;
    @MockitoBean ScoreJobPostingUseCase scoreUseCase;
    @MockitoBean MarkPostingConversionUseCase conversionUseCase;
    @MockitoBean GetApplyNowConversionStatUseCase conversionStatUseCase;
    @MockitoBean CurrentOrganizationResolver currentOrg;
    @MockitoBean JobPostingRepository jobPostingRepository;
    @MockitoBean ApiKeyRepository apiKeyRepository;
    @MockitoBean OAuth2UserService oAuth2UserService;

    private static final UUID ORG_ID = UuidFactory.newId();
    @BeforeEach
    void seedConversionStat() {
        when(conversionStatUseCase.getStat(any(UUID.class)))
                .thenReturn(ApplyNowConversionStat.EMPTY);
    }


    /** POST /envoy/postings/{id}/applied delegates to the use case and redirects. */
    @Test
    @WithMockUser(username = "robsartin")
    void markAppliedDelegatesAndRedirects() throws Exception {
        var user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        var membership = new Membership();
        membership.setUserId(user.getId());
        membership.setOrganizationId(ORG_ID);
        UUID postingId = UuidFactory.newId();
        when(currentOrg.resolve(any(UserDetails.class)))

                .thenReturn(new CurrentOrganizationResolver.Resolved(user, ORG_ID));

        mvc.perform(post("/envoy/postings/{id}/applied", postingId).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/envoy"));

        verify(conversionUseCase).markApplied(postingId, ORG_ID);
    }

    /** POST /envoy/postings/{id}/dismissed delegates to the use case and redirects. */
    @Test
    @WithMockUser(username = "robsartin")
    void dismissDelegatesAndRedirects() throws Exception {
        var user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        var membership = new Membership();
        membership.setUserId(user.getId());
        membership.setOrganizationId(ORG_ID);
        UUID postingId = UuidFactory.newId();
        when(currentOrg.resolve(any(UserDetails.class)))

                .thenReturn(new CurrentOrganizationResolver.Resolved(user, ORG_ID));

        mvc.perform(post("/envoy/postings/{id}/dismissed", postingId).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/envoy"));

        verify(conversionUseCase).dismiss(postingId, ORG_ID);
    }

    /** Unauthenticated POST is redirected to login and the use case is not invoked. */
    @Test
    void markAppliedRequiresAuth() throws Exception {
        mvc.perform(post("/envoy/postings/{id}/applied", UuidFactory.newId()).with(csrf()))
                .andExpect(status().is3xxRedirection());
        verify(conversionUseCase, never()).markApplied(any(), any());
    }
}
