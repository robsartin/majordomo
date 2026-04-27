package com.majordomo.adapter.in.web.envoy;

import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.CategoryScore;
import com.majordomo.domain.model.envoy.Disqualifier;
import com.majordomo.domain.model.envoy.FlagHit;
import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.model.identity.Membership;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.port.in.envoy.QueryScoreReportsUseCase;
import com.majordomo.domain.port.out.envoy.JobPostingRepository;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
    @MockitoBean JobPostingRepository jobPostingRepository;
    @MockitoBean ApiKeyRepository apiKeyRepository;
    @MockitoBean OAuth2UserService oAuth2UserService;

    private static final UUID ORG_ID = UuidFactory.newId();

    @Test
    @WithMockUser(username = "robsartin")
    void rendersEnvoyPageWithEnrichedRows() throws Exception {
        var user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        var membership = new Membership();
        membership.setUserId(user.getId());
        membership.setOrganizationId(ORG_ID);

        UUID postingId = UuidFactory.newId();
        var report = new ScoreReport(UuidFactory.newId(), ORG_ID, postingId,
                UuidFactory.newId(), 1, Optional.empty(),
                List.of(), List.of(), 87, 87, Recommendation.APPLY_NOW,
                "claude-sonnet-4-6", Instant.now());

        var posting = new JobPosting();
        posting.setId(postingId);
        posting.setOrganizationId(ORG_ID);
        posting.setCompany("Acme Corp");
        posting.setTitle("Senior Backend Engineer");
        posting.setLocation("Remote (US)");

        when(userRepository.findByUsername("robsartin")).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserId(user.getId())).thenReturn(List.of(membership));
        when(reports.query(eq(ORG_ID), any(), any(), any(), any(Integer.class)))
                .thenReturn(new Page<>(List.of(report), null, false));
        when(jobPostingRepository.findById(postingId, ORG_ID)).thenReturn(Optional.of(posting));

        MvcResult result = mvc.perform(get("/envoy"))
                .andExpect(status().isOk())
                .andExpect(view().name("envoy"))
                .andExpect(model().attributeExists("rows"))
                .andExpect(model().attribute("organizationId", ORG_ID))
                .andExpect(model().attribute("username", "robsartin"))
                .andReturn();

        @SuppressWarnings("unchecked")
        List<EnvoyPageController.ScoreReportRow> rows =
                (List<EnvoyPageController.ScoreReportRow>) result.getModelAndView()
                        .getModel().get("rows");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).report()).isEqualTo(report);
        assertThat(rows.get(0).posting()).isNotNull();
        assertThat(rows.get(0).posting().getCompany()).isEqualTo("Acme Corp");
        assertThat(rows.get(0).posting().getTitle()).isEqualTo("Senior Backend Engineer");
        assertThat(rows.get(0).posting().getLocation()).isEqualTo("Remote (US)");
    }

    @Test
    @WithMockUser(username = "robsartin")
    void rendersRowWithPartialPostingMetadata() throws Exception {
        var user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        var membership = new Membership();
        membership.setUserId(user.getId());
        membership.setOrganizationId(ORG_ID);

        UUID postingId = UuidFactory.newId();
        var report = new ScoreReport(UuidFactory.newId(), ORG_ID, postingId,
                UuidFactory.newId(), 1, Optional.empty(),
                List.of(), List.of(), 50, 50, Recommendation.CONSIDER,
                "claude-sonnet-4-6", Instant.now());

        // posting where LLM extraction couldn't pull company/location, only title is set
        var posting = new JobPosting();
        posting.setId(postingId);
        posting.setOrganizationId(ORG_ID);
        posting.setCompany(null);
        posting.setTitle("Software Engineer");
        posting.setLocation(null);

        when(userRepository.findByUsername("robsartin")).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserId(user.getId())).thenReturn(List.of(membership));
        when(reports.query(eq(ORG_ID), any(), any(), any(), any(Integer.class)))
                .thenReturn(new Page<>(List.of(report), null, false));
        when(jobPostingRepository.findById(postingId, ORG_ID)).thenReturn(Optional.of(posting));

        MvcResult result = mvc.perform(get("/envoy"))
                .andExpect(status().isOk())
                .andReturn();

        @SuppressWarnings("unchecked")
        List<EnvoyPageController.ScoreReportRow> rows =
                (List<EnvoyPageController.ScoreReportRow>) result.getModelAndView()
                        .getModel().get("rows");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).posting()).isNotNull();
        assertThat(rows.get(0).posting().getCompany()).isNull();
        assertThat(rows.get(0).posting().getTitle()).isEqualTo("Software Engineer");
        assertThat(rows.get(0).posting().getLocation()).isNull();
    }

    @Test
    @WithMockUser(username = "robsartin")
    void rendersRowWhenPostingNoLongerExists() throws Exception {
        var user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        var membership = new Membership();
        membership.setUserId(user.getId());
        membership.setOrganizationId(ORG_ID);

        UUID postingId = UuidFactory.newId();
        var report = new ScoreReport(UuidFactory.newId(), ORG_ID, postingId,
                UuidFactory.newId(), 1, Optional.empty(),
                List.of(), List.of(), 70, 70, Recommendation.APPLY,
                "claude-sonnet-4-6", Instant.now());

        when(userRepository.findByUsername("robsartin")).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserId(user.getId())).thenReturn(List.of(membership));
        when(reports.query(eq(ORG_ID), any(), any(), any(), any(Integer.class)))
                .thenReturn(new Page<>(List.of(report), null, false));
        when(jobPostingRepository.findById(postingId, ORG_ID)).thenReturn(Optional.empty());

        MvcResult result = mvc.perform(get("/envoy"))
                .andExpect(status().isOk())
                .andExpect(view().name("envoy"))
                .andReturn();

        @SuppressWarnings("unchecked")
        List<EnvoyPageController.ScoreReportRow> rows =
                (List<EnvoyPageController.ScoreReportRow>) result.getModelAndView()
                        .getModel().get("rows");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).report()).isEqualTo(report);
        assertThat(rows.get(0).posting()).isNull();
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

    // -------------------- filter tests (issue #146) --------------------

    @Test
    @WithMockUser(username = "robsartin")
    void unfilteredRequestPassesNullsThrough() throws Exception {
        var user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        var membership = new Membership();
        membership.setUserId(user.getId());
        membership.setOrganizationId(ORG_ID);

        when(userRepository.findByUsername("robsartin")).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserId(user.getId())).thenReturn(List.of(membership));
        when(reports.query(eq(ORG_ID), any(), any(), any(), any(Integer.class)))
                .thenReturn(new Page<>(List.of(), null, false));

        mvc.perform(get("/envoy"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("minFinalScore", (Object) null))
                .andExpect(model().attribute("recommendation", (Object) null));

        verify(reports).query(eq(ORG_ID), isNull(), isNull(), isNull(), anyInt());
    }

    @Test
    @WithMockUser(username = "robsartin")
    void filtersByRecommendationOnly() throws Exception {
        var user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        var membership = new Membership();
        membership.setUserId(user.getId());
        membership.setOrganizationId(ORG_ID);

        when(userRepository.findByUsername("robsartin")).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserId(user.getId())).thenReturn(List.of(membership));
        when(reports.query(eq(ORG_ID), any(), any(), any(), any(Integer.class)))
                .thenReturn(new Page<>(List.of(), null, false));

        mvc.perform(get("/envoy").param("recommendation", "APPLY_NOW"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("minFinalScore", (Object) null))
                .andExpect(model().attribute("recommendation", Recommendation.APPLY_NOW));

        verify(reports).query(eq(ORG_ID), isNull(),
                eq(Recommendation.APPLY_NOW), isNull(), anyInt());
    }

    @Test
    @WithMockUser(username = "robsartin")
    void filtersByMinFinalScoreOnly() throws Exception {
        var user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        var membership = new Membership();
        membership.setUserId(user.getId());
        membership.setOrganizationId(ORG_ID);

        when(userRepository.findByUsername("robsartin")).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserId(user.getId())).thenReturn(List.of(membership));
        when(reports.query(eq(ORG_ID), any(), any(), any(), any(Integer.class)))
                .thenReturn(new Page<>(List.of(), null, false));

        mvc.perform(get("/envoy").param("minFinalScore", "70"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("minFinalScore", 70))
                .andExpect(model().attribute("recommendation", (Object) null));

        verify(reports).query(eq(ORG_ID), eq(70), isNull(), isNull(), anyInt());
    }

    @Test
    @WithMockUser(username = "robsartin")
    void filtersByBothMinFinalScoreAndRecommendation() throws Exception {
        var user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        var membership = new Membership();
        membership.setUserId(user.getId());
        membership.setOrganizationId(ORG_ID);

        when(userRepository.findByUsername("robsartin")).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserId(user.getId())).thenReturn(List.of(membership));
        when(reports.query(eq(ORG_ID), any(), any(), any(), any(Integer.class)))
                .thenReturn(new Page<>(List.of(), null, false));

        mvc.perform(get("/envoy")
                        .param("minFinalScore", "70")
                        .param("recommendation", "APPLY_NOW"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("minFinalScore", 70))
                .andExpect(model().attribute("recommendation", Recommendation.APPLY_NOW));

        verify(reports).query(eq(ORG_ID), eq(70),
                eq(Recommendation.APPLY_NOW), isNull(), anyInt());
    }

    @Test
    @WithMockUser(username = "robsartin")
    void rendersFilterStripWithPrePopulatedValues() throws Exception {
        var user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        var membership = new Membership();
        membership.setUserId(user.getId());
        membership.setOrganizationId(ORG_ID);

        when(userRepository.findByUsername("robsartin")).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserId(user.getId())).thenReturn(List.of(membership));
        when(reports.query(eq(ORG_ID), any(), any(), any(), any(Integer.class)))
                .thenReturn(new Page<>(List.of(), null, false));

        MvcResult result = mvc.perform(get("/envoy")
                        .param("minFinalScore", "70")
                        .param("recommendation", "APPLY_NOW"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        // form should target /envoy via GET
        assertThat(body).contains("action=\"/envoy\"");
        // min score input should be pre-populated
        assertThat(body).contains("name=\"minFinalScore\"");
        assertThat(body).contains("value=\"70\"");
        // recommendation select option for APPLY_NOW should be selected
        assertThat(body).contains("name=\"recommendation\"");
        assertThat(body).contains("APPLY_NOW");
    }

    // -------------------- detail page tests (issue #145) --------------------

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

        when(userRepository.findByUsername("robsartin")).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserId(user.getId())).thenReturn(List.of(membership));
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

        when(userRepository.findByUsername("robsartin")).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserId(user.getId())).thenReturn(List.of(membership));
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

        when(userRepository.findByUsername("robsartin")).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserId(user.getId())).thenReturn(List.of(membership));
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

        when(userRepository.findByUsername("robsartin")).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserId(user.getId())).thenReturn(List.of(membership));
        when(reports.findById(reportId, ORG_ID)).thenReturn(Optional.empty());

        mvc.perform(get("/envoy/reports/{id}", reportId))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "robsartin")
    void getReport_redirectsHomeWhenUserHasNoMembership() throws Exception {
        var user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        when(userRepository.findByUsername("robsartin")).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserId(user.getId())).thenReturn(List.of());

        mvc.perform(get("/envoy/reports/{id}", UuidFactory.newId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/"));
    }

    @Test
    void getReport_unauthenticatedRedirectsToLogin() throws Exception {
        mvc.perform(get("/envoy/reports/{id}", UuidFactory.newId()))
                .andExpect(status().is3xxRedirection());
    }
}
