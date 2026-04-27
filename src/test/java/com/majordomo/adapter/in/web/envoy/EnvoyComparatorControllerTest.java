package com.majordomo.adapter.in.web.envoy;

import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.Category;
import com.majordomo.domain.model.envoy.CategoryScore;
import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.envoy.Rubric;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.model.envoy.Thresholds;
import com.majordomo.domain.model.envoy.Tier;
import com.majordomo.domain.model.identity.Membership;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.port.in.envoy.QueryScoreReportsUseCase;
import com.majordomo.domain.port.out.envoy.JobPostingRepository;
import com.majordomo.domain.port.out.envoy.RubricRepository;
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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(EnvoyComparatorController.class)
@Import(SecurityConfig.class)
class EnvoyComparatorControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean QueryScoreReportsUseCase reports;
    @MockitoBean RubricRepository rubricRepository;
    @MockitoBean JobPostingRepository jobPostingRepository;
    @MockitoBean UserRepository userRepository;
    @MockitoBean MembershipRepository membershipRepository;
    @MockitoBean ApiKeyRepository apiKeyRepository;
    @MockitoBean OAuth2UserService oAuth2UserService;

    private static final UUID ORG_ID = UuidFactory.newId();

    private Rubric rubric() {
        return new Rubric(
                UuidFactory.newId(),
                Optional.of(ORG_ID),
                1,
                "default",
                List.of(),
                List.of(
                        new Category("compensation", "Comp", 30,
                                List.of(new Tier("Excellent", 30, "x"),
                                        new Tier("Good", 20, "x"))),
                        new Category("remote", "Remote", 20,
                                List.of(new Tier("Fully", 20, "x")))),
                List.of(),
                new Thresholds(80, 60, 40),
                Instant.now());
    }

    private ScoreReport report(UUID postingId, int finalScore, Recommendation rec,
                               UUID rubricId, List<CategoryScore> scores) {
        return new ScoreReport(
                UuidFactory.newId(), ORG_ID, postingId, rubricId, 1,
                Optional.empty(), scores, List.of(),
                finalScore, finalScore, rec, "claude-sonnet-4-6", Instant.now());
    }

    private JobPosting posting(UUID id, String company, String title, String location) {
        var p = new JobPosting();
        p.setId(id);
        p.setOrganizationId(ORG_ID);
        p.setCompany(company);
        p.setTitle(title);
        p.setLocation(location);
        return p;
    }

    private void stubAuthedUser() {
        var user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        var membership = new Membership();
        membership.setUserId(user.getId());
        membership.setOrganizationId(ORG_ID);
        when(userRepository.findByUsername("robsartin")).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserId(user.getId())).thenReturn(List.of(membership));
    }

    @Test
    @WithMockUser(username = "robsartin")
    void rendersComparisonForTwoValidReportsUnderSameRubric() throws Exception {
        stubAuthedUser();
        Rubric r = rubric();
        when(rubricRepository.findActiveByName("default", ORG_ID)).thenReturn(Optional.of(r));

        UUID postingA = UuidFactory.newId();
        UUID postingB = UuidFactory.newId();
        var reportA = report(postingA, 85, Recommendation.APPLY_NOW, r.id(), List.of(
                new CategoryScore("compensation", 30, "Excellent", "Top of band"),
                new CategoryScore("remote", 20, "Fully", "Remote-first")));
        var reportB = report(postingB, 60, Recommendation.APPLY, r.id(), List.of(
                new CategoryScore("compensation", 20, "Good", "Mid band"),
                new CategoryScore("remote", 20, "Fully", "Remote-first")));

        when(reports.findById(reportA.id(), ORG_ID)).thenReturn(Optional.of(reportA));
        when(reports.findById(reportB.id(), ORG_ID)).thenReturn(Optional.of(reportB));
        when(jobPostingRepository.findById(postingA, ORG_ID))
                .thenReturn(Optional.of(posting(postingA, "Acme", "SBE", "Remote")));
        when(jobPostingRepository.findById(postingB, ORG_ID))
                .thenReturn(Optional.of(posting(postingB, "Globex", "BE", "NYC")));

        MvcResult result = mvc.perform(get("/envoy/compare")
                        .param("ids", reportA.id() + "," + reportB.id()))
                .andExpect(status().isOk())
                .andExpect(view().name("envoy-compare"))
                .andExpect(model().attributeExists("columns"))
                .andExpect(model().attributeExists("rubric"))
                .andExpect(model().attributeExists("rows"))
                .andReturn();

        // Two columns, both scored, all rubric categories represented in rows.
        @SuppressWarnings("unchecked")
        List<EnvoyComparatorController.Column> columns =
                (List<EnvoyComparatorController.Column>)
                        result.getModelAndView().getModel().get("columns");
        assertThat(columns).hasSize(2);
        assertThat(columns).allMatch(c -> c.report() != null);

        @SuppressWarnings("unchecked")
        List<EnvoyComparatorController.Row> rows =
                (List<EnvoyComparatorController.Row>)
                        result.getModelAndView().getModel().get("rows");
        assertThat(rows).hasSize(2);
        assertThat(rows.stream().map(EnvoyComparatorController.Row::categoryKey).toList())
                .containsExactly("compensation", "remote");

        // Highest highlight on compensation should belong to column 0 (reportA: 30 > 20).
        var compRow = rows.get(0);
        assertThat(compRow.cells().get(0).isHighest()).isTrue();
        assertThat(compRow.cells().get(1).isHighest()).isFalse();
        // Tie on remote (20 vs 20): both should highlight.
        var remoteRow = rows.get(1);
        assertThat(remoteRow.cells().stream().allMatch(EnvoyComparatorController.Cell::isHighest))
                .isTrue();

        // Footer summary: gap from APPLY threshold (60) per posting.
        assertThat(columns.get(0).gapFromApplyThreshold()).isEqualTo(85 - 60);
        assertThat(columns.get(1).gapFromApplyThreshold()).isEqualTo(60 - 60);
    }

    @Test
    @WithMockUser(username = "robsartin")
    void returns400WhenFewerThanTwoIds() throws Exception {
        stubAuthedUser();
        when(rubricRepository.findActiveByName("default", ORG_ID))
                .thenReturn(Optional.of(rubric()));

        mvc.perform(get("/envoy/compare").param("ids", UuidFactory.newId().toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "robsartin")
    void returns400WhenMoreThanFiveIds() throws Exception {
        stubAuthedUser();
        when(rubricRepository.findActiveByName("default", ORG_ID))
                .thenReturn(Optional.of(rubric()));

        String tooMany = String.join(",", Stream.generate(() -> UuidFactory.newId().toString())
                .limit(6).toList());

        mvc.perform(get("/envoy/compare").param("ids", tooMany))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "robsartin")
    void marksPostingAsNotScoredWhenNoReportForRequestedRubric() throws Exception {
        stubAuthedUser();
        Rubric r = rubric();
        UUID otherRubricId = UuidFactory.newId();
        when(rubricRepository.findActiveByName("default", ORG_ID)).thenReturn(Optional.of(r));

        UUID postingA = UuidFactory.newId();
        UUID postingB = UuidFactory.newId();
        var reportA = report(postingA, 70, Recommendation.APPLY, r.id(), List.of(
                new CategoryScore("compensation", 30, "Excellent", "Top"),
                new CategoryScore("remote", 20, "Fully", "Remote")));
        // reportB is scored under a *different* rubric — should surface as "not scored".
        var reportB = report(postingB, 50, Recommendation.CONSIDER, otherRubricId, List.of(
                new CategoryScore("compensation", 20, "Good", "Mid"),
                new CategoryScore("remote", 20, "Fully", "Remote")));

        when(reports.findById(reportA.id(), ORG_ID)).thenReturn(Optional.of(reportA));
        when(reports.findById(reportB.id(), ORG_ID)).thenReturn(Optional.of(reportB));
        when(jobPostingRepository.findById(postingA, ORG_ID))
                .thenReturn(Optional.of(posting(postingA, "Acme", "SBE", "Remote")));
        when(jobPostingRepository.findById(postingB, ORG_ID))
                .thenReturn(Optional.of(posting(postingB, "Globex", "BE", "NYC")));

        MvcResult result = mvc.perform(get("/envoy/compare")
                        .param("ids", reportA.id() + "," + reportB.id()))
                .andExpect(status().isOk())
                .andExpect(view().name("envoy-compare"))
                .andReturn();

        @SuppressWarnings("unchecked")
        List<EnvoyComparatorController.Column> columns =
                (List<EnvoyComparatorController.Column>)
                        result.getModelAndView().getModel().get("columns");
        assertThat(columns).hasSize(2);
        assertThat(columns.get(0).report()).isNotNull();
        assertThat(columns.get(0).notScored()).isFalse();
        assertThat(columns.get(1).report()).isNull();
        assertThat(columns.get(1).notScored()).isTrue();
        // Posting metadata still surfaced for the not-scored column.
        assertThat(columns.get(1).posting()).isNotNull();
    }

    @Test
    @WithMockUser(username = "robsartin")
    void redirectsHomeWhenUserHasNoMembership() throws Exception {
        var user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        when(userRepository.findByUsername("robsartin")).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserId(user.getId())).thenReturn(List.of());

        mvc.perform(get("/envoy/compare")
                        .param("ids", UuidFactory.newId() + "," + UuidFactory.newId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/"));
    }

    @Test
    void unauthenticatedRedirectsToLogin() throws Exception {
        mvc.perform(get("/envoy/compare")
                        .param("ids", UuidFactory.newId() + "," + UuidFactory.newId()))
                .andExpect(status().is3xxRedirection());
    }
}
