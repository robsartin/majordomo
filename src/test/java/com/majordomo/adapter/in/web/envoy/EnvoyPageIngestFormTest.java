package com.majordomo.adapter.in.web.envoy;

import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.application.envoy.LlmScoringException;
import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.JobSourceRequest;
import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.model.identity.Membership;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.port.in.envoy.IngestJobPostingUseCase;
import com.majordomo.domain.port.in.envoy.MarkPostingConversionUseCase;
import com.majordomo.domain.port.in.envoy.QueryScoreReportsUseCase;
import com.majordomo.domain.port.in.envoy.ScoreJobPostingUseCase;
import com.majordomo.domain.port.out.envoy.JobPostingRepository;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;
import com.majordomo.domain.port.out.identity.MembershipRepository;
import com.majordomo.domain.port.out.identity.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(EnvoyPageController.class)
@Import(SecurityConfig.class)
class EnvoyPageIngestFormTest {

    @Autowired MockMvc mvc;

    @MockitoBean QueryScoreReportsUseCase reports;
    @MockitoBean IngestJobPostingUseCase ingestUseCase;
    @MockitoBean ScoreJobPostingUseCase scoreUseCase;
    @MockitoBean MarkPostingConversionUseCase conversionUseCase;
    @MockitoBean UserRepository userRepository;
    @MockitoBean MembershipRepository membershipRepository;
    @MockitoBean JobPostingRepository jobPostingRepository;
    @MockitoBean ApiKeyRepository apiKeyRepository;
    @MockitoBean OAuth2UserService oAuth2UserService;

    private static final UUID ORG_ID = UuidFactory.newId();

    @Test
    @WithMockUser(username = "robsartin")
    void postEnvoy_ingestsAndScoresAndRedirects() throws Exception {
        var user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        var membership = new Membership();
        membership.setUserId(user.getId());
        membership.setOrganizationId(ORG_ID);

        UUID postingId = UuidFactory.newId();
        var saved = new JobPosting();
        saved.setId(postingId);
        saved.setOrganizationId(ORG_ID);

        var report = new ScoreReport(UuidFactory.newId(), ORG_ID, postingId,
                UuidFactory.newId(), 1, Optional.empty(),
                List.of(), List.of(), 90, 90, Recommendation.APPLY_NOW,
                "claude-sonnet-4-6", Instant.now());

        when(userRepository.findByUsername("robsartin")).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserId(user.getId())).thenReturn(List.of(membership));
        when(ingestUseCase.ingest(any(JobSourceRequest.class), eq(ORG_ID))).thenReturn(saved);
        when(scoreUseCase.score(eq(postingId), eq("default"), eq(ORG_ID))).thenReturn(report);

        mvc.perform(post("/envoy")
                        .with(csrf())
                        .param("type", "manual")
                        .param("payload", "Senior Backend Engineer at Acme")
                        .param("company", "Acme")
                        .param("title", "Senior Backend Engineer")
                        .param("location", "Remote (US)"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/envoy"));

        ArgumentCaptor<JobSourceRequest> captor = ArgumentCaptor.forClass(JobSourceRequest.class);
        verify(ingestUseCase).ingest(captor.capture(), eq(ORG_ID));
        JobSourceRequest req = captor.getValue();
        assertThat(req.type()).isEqualTo("manual");
        assertThat(req.payload()).isEqualTo("Senior Backend Engineer at Acme");
        assertThat(req.hints()).containsEntry("company", "Acme");
        assertThat(req.hints()).containsEntry("title", "Senior Backend Engineer");
        assertThat(req.hints()).containsEntry("location", "Remote (US)");

        verify(scoreUseCase).score(postingId, "default", ORG_ID);
    }

    @Test
    @WithMockUser(username = "robsartin")
    void postEnvoy_omitsBlankHints() throws Exception {
        var user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        var membership = new Membership();
        membership.setUserId(user.getId());
        membership.setOrganizationId(ORG_ID);

        UUID postingId = UuidFactory.newId();
        var saved = new JobPosting();
        saved.setId(postingId);
        saved.setOrganizationId(ORG_ID);

        var report = new ScoreReport(UuidFactory.newId(), ORG_ID, postingId,
                UuidFactory.newId(), 1, Optional.empty(),
                List.of(), List.of(), 50, 50, Recommendation.CONSIDER,
                "claude-sonnet-4-6", Instant.now());

        when(userRepository.findByUsername("robsartin")).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserId(user.getId())).thenReturn(List.of(membership));
        when(ingestUseCase.ingest(any(JobSourceRequest.class), eq(ORG_ID))).thenReturn(saved);
        when(scoreUseCase.score(eq(postingId), eq("default"), eq(ORG_ID))).thenReturn(report);

        mvc.perform(post("/envoy")
                        .with(csrf())
                        .param("type", "manual")
                        .param("payload", "Some posting body")
                        .param("company", "")
                        .param("title", "  ")
                        .param("location", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/envoy"));

        ArgumentCaptor<JobSourceRequest> captor = ArgumentCaptor.forClass(JobSourceRequest.class);
        verify(ingestUseCase).ingest(captor.capture(), eq(ORG_ID));
        assertThat(captor.getValue().hints()).isEmpty();
    }

    @Test
    @WithMockUser(username = "robsartin")
    void postEnvoy_renderEnvoyWithErrorWhenIngestThrows() throws Exception {
        var user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        var membership = new Membership();
        membership.setUserId(user.getId());
        membership.setOrganizationId(ORG_ID);

        when(userRepository.findByUsername("robsartin")).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserId(user.getId())).thenReturn(List.of(membership));
        when(ingestUseCase.ingest(any(JobSourceRequest.class), eq(ORG_ID)))
                .thenThrow(new IllegalArgumentException("no JobSource supports type: bogus"));
        when(reports.query(eq(ORG_ID), any(), any(), any(), any(Integer.class)))
                .thenReturn(new Page<>(List.of(), null, false));

        mvc.perform(post("/envoy")
                        .with(csrf())
                        .param("type", "bogus")
                        .param("payload", "Some posting body"))
                .andExpect(status().isOk())
                .andExpect(view().name("envoy"))
                .andExpect(model().attribute("ingestError",
                        containsString("no JobSource supports type: bogus")));

        verify(scoreUseCase, never()).score(any(), any(), any());
    }

    @Test
    @WithMockUser(username = "robsartin")
    void postEnvoy_renderEnvoyWithErrorWhenScoreThrows() throws Exception {
        var user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        var membership = new Membership();
        membership.setUserId(user.getId());
        membership.setOrganizationId(ORG_ID);

        UUID postingId = UuidFactory.newId();
        var saved = new JobPosting();
        saved.setId(postingId);
        saved.setOrganizationId(ORG_ID);

        when(userRepository.findByUsername("robsartin")).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserId(user.getId())).thenReturn(List.of(membership));
        when(ingestUseCase.ingest(any(JobSourceRequest.class), eq(ORG_ID))).thenReturn(saved);
        when(scoreUseCase.score(eq(postingId), eq("default"), eq(ORG_ID)))
                .thenThrow(new LlmScoringException("LLM returned malformed JSON"));
        when(reports.query(eq(ORG_ID), any(), any(), any(), any(Integer.class)))
                .thenReturn(new Page<>(List.of(), null, false));

        mvc.perform(post("/envoy")
                        .with(csrf())
                        .param("type", "manual")
                        .param("payload", "Some posting body"))
                .andExpect(status().isOk())
                .andExpect(view().name("envoy"))
                .andExpect(model().attribute("ingestError",
                        containsString("LLM returned malformed JSON")));
    }

    @Test
    void postEnvoy_unauthenticatedRedirectsToLogin() throws Exception {
        mvc.perform(post("/envoy")
                        .with(csrf())
                        .param("type", "manual")
                        .param("payload", "Some posting body"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "robsartin")
    void postEnvoy_redirectsHomeWhenUserHasNoMembership() throws Exception {
        var user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        when(userRepository.findByUsername("robsartin")).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserId(user.getId())).thenReturn(List.of());

        mvc.perform(post("/envoy")
                        .with(csrf())
                        .param("type", "manual")
                        .param("payload", "Some posting body"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/"));

        verify(ingestUseCase, never()).ingest(any(), any());
        verify(scoreUseCase, never()).score(any(), any(), any());
    }

    @Test
    @WithMockUser(username = "robsartin")
    void postEnvoy_rejectsMissingCsrf() throws Exception {
        mvc.perform(post("/envoy")
                        .param("type", "manual")
                        .param("payload", "Some posting body"))
                .andExpect(status().isForbidden());

        verify(ingestUseCase, never()).ingest(any(), any());
    }

    @Test
    @WithMockUser(username = "robsartin")
    void postEnvoy_rendersEnvoyWithErrorWhenPayloadIsBlank() throws Exception {
        var user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        var membership = new Membership();
        membership.setUserId(user.getId());
        membership.setOrganizationId(ORG_ID);

        when(userRepository.findByUsername("robsartin")).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserId(user.getId())).thenReturn(List.of(membership));
        when(reports.query(eq(ORG_ID), any(), any(), any(), any(Integer.class)))
                .thenReturn(new Page<>(List.of(), null, false));

        mvc.perform(post("/envoy")
                        .with(csrf())
                        .param("type", "manual")
                        .param("payload", "   "))
                .andExpect(status().isOk())
                .andExpect(view().name("envoy"))
                .andExpect(model().attributeExists("ingestError"));

        verify(ingestUseCase, never()).ingest(any(), any());
        verify(scoreUseCase, never()).score(any(), any(), any());
    }
}
