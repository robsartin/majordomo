package com.majordomo.adapter.in.web.envoy;

import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.CategoryScore;
import com.majordomo.domain.model.envoy.Disqualifier;
import com.majordomo.domain.model.envoy.FlagHit;
import com.majordomo.domain.model.envoy.ApplicationMaterial;
import com.majordomo.domain.model.envoy.ClaimCitation;
import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.MaterialKind;
import com.majordomo.domain.model.envoy.Tone;
import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.envoy.ScoreReport;
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
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * The report detail page at {@code /envoy/reports/{id}} (#352).
 *
 * <p>Split out of {@code EnvoyPageControllerTest}, which had reached the
 * 500-line limit: adding one mock bean for the drafts repository pushed it
 * over, and the detail-page tests were already a separate subject from the
 * list page's.
 */
@WebMvcTest(EnvoyPageController.class)
@Import(SecurityConfig.class)
class EnvoyReportPageTest {

    @Autowired MockMvc mvc;

    @MockitoBean QueryScoreReportsUseCase reports;
    @MockitoBean com.majordomo.domain.port.out.envoy.ApplicationMaterialRepository materials;
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

    @Test
    @WithMockUser(username = "robsartin")
    void getReport_rendersDetailPage() throws Exception {
        var user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        var membership = new Membership();
        membership.setUserId(user.getId());
        membership.setOrganizationId(ORG_ID);

        UUID reportId = UuidFactory.newId();
        UUID postingId = UuidFactory.newId();

        var category = new CategoryScore("compensation", 25, "Strong",
                "Base salary listed at $200k.");
        var flag = new FlagHit("legacy_stack", 5, "Mentions COBOL.");

        var report = new ScoreReport(reportId, ORG_ID, postingId,
                UuidFactory.newId(), 3, Optional.empty(),
                List.of(category), List.of(flag), 80, 75,
                Recommendation.APPLY, "claude-sonnet-4-6", Instant.now());

        var posting = new JobPosting();
        posting.setId(postingId);
        posting.setOrganizationId(ORG_ID);
        posting.setSource("greenhouse");
        posting.setExternalId("ext-1234");
        posting.setCompany("Acme Corp");
        posting.setTitle("Senior Backend Engineer");
        posting.setLocation("Remote (US)");
        posting.setRawText("Full posting body goes here.");

        when(currentOrg.resolve(any(UserDetails.class)))
                .thenReturn(new CurrentOrganizationResolver.Resolved(user, ORG_ID));
        when(reports.findById(reportId, ORG_ID)).thenReturn(Optional.of(report));
        when(jobPostingRepository.findById(postingId, ORG_ID)).thenReturn(Optional.of(posting));

        MvcResult result = mvc.perform(get("/envoy/reports/{id}", reportId))
                .andExpect(status().isOk())
                .andExpect(view().name("envoy-report"))
                .andExpect(model().attribute("report", report))
                .andExpect(model().attribute("posting", posting))
                .andExpect(model().attribute("organizationId", ORG_ID))
                .andExpect(model().attribute("username", "robsartin"))
                .andExpect(content().string(containsString("Acme Corp")))
                .andExpect(content().string(containsString("Senior Backend Engineer")))
                .andExpect(content().string(containsString("Base salary listed at $200k.")))
                .andExpect(content().string(containsString("Mentions COBOL.")))
                .andExpect(content().string(containsString("Full posting body goes here.")))
                .andReturn();

        // sidebar should have envoy highlighted
        assertThat(result.getResponse().getContentAsString()).contains("envoy");
    }

    @Test
    @WithMockUser(username = "robsartin")
    void getReport_rendersDisqualifiedBanner() throws Exception {
        var user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        var membership = new Membership();
        membership.setUserId(user.getId());
        membership.setOrganizationId(ORG_ID);

        UUID reportId = UuidFactory.newId();
        UUID postingId = UuidFactory.newId();

        var dq = new Disqualifier("ON_SITE_ONLY",
                "Posting requires 5 days a week in office.");

        var report = new ScoreReport(reportId, ORG_ID, postingId,
                UuidFactory.newId(), 3, Optional.of(dq),
                List.of(), List.of(), 50, 0,
                Recommendation.SKIP, "claude-sonnet-4-6", Instant.now());

        var posting = new JobPosting();
        posting.setId(postingId);
        posting.setOrganizationId(ORG_ID);
        posting.setSource("manual");
        posting.setExternalId("ext-9");
        posting.setCompany("RTO Co");
        posting.setTitle("Engineer");
        posting.setLocation("San Francisco, CA");
        posting.setRawText("Body.");

        when(currentOrg.resolve(any(UserDetails.class)))
                .thenReturn(new CurrentOrganizationResolver.Resolved(user, ORG_ID));
        when(reports.findById(reportId, ORG_ID)).thenReturn(Optional.of(report));
        when(jobPostingRepository.findById(postingId, ORG_ID)).thenReturn(Optional.of(posting));

        mvc.perform(get("/envoy/reports/{id}", reportId))
                .andExpect(status().isOk())
                .andExpect(view().name("envoy-report"))
                .andExpect(content().string(containsString("ON_SITE_ONLY")))
                .andExpect(content().string(
                        containsString("Posting requires 5 days a week in office.")));
    }

    @Test
    @WithMockUser(username = "robsartin")
    void getReport_rendersWhenPostingIsGone() throws Exception {
        var user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        var membership = new Membership();
        membership.setUserId(user.getId());
        membership.setOrganizationId(ORG_ID);

        UUID reportId = UuidFactory.newId();
        UUID postingId = UuidFactory.newId();

        var report = new ScoreReport(reportId, ORG_ID, postingId,
                UuidFactory.newId(), 1, Optional.empty(),
                List.of(), List.of(), 60, 60,
                Recommendation.CONSIDER, "claude-sonnet-4-6", Instant.now());

        when(currentOrg.resolve(any(UserDetails.class)))
                .thenReturn(new CurrentOrganizationResolver.Resolved(user, ORG_ID));
        when(reports.findById(reportId, ORG_ID)).thenReturn(Optional.of(report));
        when(jobPostingRepository.findById(postingId, ORG_ID)).thenReturn(Optional.empty());

        mvc.perform(get("/envoy/reports/{id}", reportId))
                .andExpect(status().isOk())
                .andExpect(view().name("envoy-report"))
                .andExpect(model().attribute("report", report))
                .andExpect(model().attributeDoesNotExist("posting"));
    }

    @Test
    @WithMockUser(username = "robsartin")
    void getReport_returns404WhenNotFound() throws Exception {
        var user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        var membership = new Membership();
        membership.setUserId(user.getId());
        membership.setOrganizationId(ORG_ID);

        UUID reportId = UuidFactory.newId();

        when(currentOrg.resolve(any(UserDetails.class)))
                .thenReturn(new CurrentOrganizationResolver.Resolved(user, ORG_ID));
        when(reports.findById(reportId, ORG_ID)).thenReturn(Optional.empty());

        mvc.perform(get("/envoy/reports/{id}", reportId))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "robsartin")
    void getReport_redirectsHomeWhenUserHasNoMembership() throws Exception {
        var user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        when(currentOrg.resolve(any(UserDetails.class)))
                .thenReturn(new CurrentOrganizationResolver.Resolved(user, null));

        mvc.perform(get("/envoy/reports/{id}", UuidFactory.newId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/"));
    }

    @Test
    void getReport_unauthenticatedRedirectsToLogin() throws Exception {
        mvc.perform(get("/envoy/reports/{id}", UuidFactory.newId()))
                .andExpect(status().is3xxRedirection());
    }

    /**
     * Drafts are never overwritten (ADR-0028), so the page shows a posting's
     * history rather than only the newest — and it offers the kinds and tones
     * to choose between.
     */
    @Test
    @WithMockUser(username = "robsartin")
    void getReport_exposesPreviousDraftsAndTheChoices() throws Exception {
        var user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        UUID reportId = UuidFactory.newId();
        UUID postingId = UuidFactory.newId();
        var report = new ScoreReport(reportId, ORG_ID, postingId,
                UuidFactory.newId(), 3, Optional.empty(),
                List.of(), List.of(), 80, 75,
                Recommendation.APPLY, "claude-sonnet-4-6", Instant.now());
        var draft = new ApplicationMaterial(
                UuidFactory.newId(), ORG_ID, postingId, Optional.of(reportId),
                MaterialKind.COVER_LETTER, Tone.DIRECT, "Dear hiring manager",
                List.of(new ClaimCitation("Led a team", "Led a team of 4 engineers")),
                "claude-opus-5", Instant.now(), Optional.empty());

        when(currentOrg.resolve(any(UserDetails.class)))
                .thenReturn(new CurrentOrganizationResolver.Resolved(user, ORG_ID));
        when(reports.findById(reportId, ORG_ID)).thenReturn(Optional.of(report));
        when(materials.findByPosting(postingId, ORG_ID)).thenReturn(List.of(draft));

        mvc.perform(get("/envoy/reports/{id}", reportId))
                .andExpect(status().isOk())
                .andExpect(model().attribute("materials", List.of(draft)))
                .andExpect(model().attributeExists("materialKinds"))
                .andExpect(model().attributeExists("tones"));
    }
}
