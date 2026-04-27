package com.majordomo.adapter.in.web.envoy;

import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.Category;
import com.majordomo.domain.model.envoy.Rubric;
import com.majordomo.domain.model.envoy.Thresholds;
import com.majordomo.domain.model.envoy.Tier;
import com.majordomo.domain.model.identity.Membership;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.port.in.envoy.ManageRubricUseCase;
import com.majordomo.domain.port.out.envoy.RubricRepository;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(RubricAuthorController.class)
@Import(SecurityConfig.class)
class RubricAuthorControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean ManageRubricUseCase rubrics;
    @MockitoBean RubricRepository rubricRepository;
    @MockitoBean UserRepository userRepository;
    @MockitoBean MembershipRepository membershipRepository;
    @MockitoBean ApiKeyRepository apiKeyRepository;
    @MockitoBean OAuth2UserService oAuth2UserService;

    private static final UUID ORG_ID = UuidFactory.newId();

    private void stubMembership(String username) {
        var user = new User(UuidFactory.newId(), username, username + "@example.com");
        var membership = new Membership();
        membership.setUserId(user.getId());
        membership.setOrganizationId(ORG_ID);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserId(user.getId())).thenReturn(List.of(membership));
    }

    private static Rubric sampleRubric(String name, int version) {
        return new Rubric(
                UuidFactory.newId(),
                Optional.of(ORG_ID),
                version,
                name,
                List.of(),
                List.of(new Category("compensation", "Pay", 25,
                        List.of(new Tier("Excellent", 25, "great")))),
                List.of(),
                new Thresholds(75, 55, 35),
                Instant.now());
    }

    // 1. GET list page shows org's rubrics.
    @Test
    @WithMockUser(username = "robsartin")
    void listPage_showsOrgsRubrics() throws Exception {
        stubMembership("robsartin");
        var defaultRubric = sampleRubric("default", 3);
        var customRubric = sampleRubric("aggressive", 1);
        when(rubricRepository.findActiveRubricsForOrg(ORG_ID))
                .thenReturn(List.of(defaultRubric, customRubric));

        MvcResult result = mvc.perform(get("/envoy/rubrics"))
                .andExpect(status().isOk())
                .andExpect(view().name("rubrics-list"))
                .andExpect(model().attribute("organizationId", ORG_ID))
                .andReturn();

        @SuppressWarnings("unchecked")
        List<Rubric> shown = (List<Rubric>) result.getModelAndView().getModel().get("rubrics");
        assertThat(shown).hasSize(2);
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("default");
        assertThat(body).contains("aggressive");
    }

    // 2. GET edit page for existing rubric pre-populates fields.
    @Test
    @WithMockUser(username = "robsartin")
    void editPage_existingRubricPrepopulatesFields() throws Exception {
        stubMembership("robsartin");
        var existing = sampleRubric("default", 3);
        when(rubricRepository.findActiveByName("default", ORG_ID))
                .thenReturn(Optional.of(existing));

        MvcResult result = mvc.perform(get("/envoy/rubrics/{name}/edit", "default"))
                .andExpect(status().isOk())
                .andExpect(view().name("rubric-edit"))
                .andExpect(model().attributeExists("form"))
                .andExpect(model().attribute("rubricName", "default"))
                .andExpect(model().attribute("isNew", false))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("compensation");
        assertThat(body).contains("Excellent");
    }

    // 3. GET edit page for new name shows blank form.
    @Test
    @WithMockUser(username = "robsartin")
    void editPage_newNameShowsBlankForm() throws Exception {
        stubMembership("robsartin");
        when(rubricRepository.findActiveByName("brand-new", ORG_ID))
                .thenReturn(Optional.empty());

        mvc.perform(get("/envoy/rubrics/{name}/edit", "brand-new"))
                .andExpect(status().isOk())
                .andExpect(view().name("rubric-edit"))
                .andExpect(model().attribute("rubricName", "brand-new"))
                .andExpect(model().attribute("isNew", true))
                .andExpect(model().attributeExists("form"));
    }

    // 4. POST valid form calls saveNewVersion with a correctly assembled Rubric.
    @Test
    @WithMockUser(username = "robsartin")
    void postValid_callsSaveNewVersionWithAssembledRubric() throws Exception {
        stubMembership("robsartin");
        var saved = sampleRubric("default", 4);
        when(rubrics.saveNewVersion(eq("default"), any(), eq(ORG_ID))).thenReturn(saved);

        mvc.perform(post("/envoy/rubrics/{name}", "default")
                        .with(csrf())
                        .param("categories[0].key", "compensation")
                        .param("categories[0].description", "Pay and equity")
                        .param("categories[0].maxPoints", "25")
                        .param("categories[0].tiers[0].label", "Excellent")
                        .param("categories[0].tiers[0].points", "25")
                        .param("categories[0].tiers[0].criteria", "Top of market")
                        .param("categories[0].tiers[1].label", "Good")
                        .param("categories[0].tiers[1].points", "15")
                        .param("categories[0].tiers[1].criteria", "Market")
                        .param("thresholds.applyImmediately", "75")
                        .param("thresholds.apply", "55")
                        .param("thresholds.considerOnly", "35"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/envoy/rubrics"))
                .andExpect(flash().attributeExists("flashMessage"));

        ArgumentCaptor<Rubric> captor = ArgumentCaptor.forClass(Rubric.class);
        verify(rubrics).saveNewVersion(eq("default"), captor.capture(), eq(ORG_ID));
        Rubric submitted = captor.getValue();
        assertThat(submitted.name()).isEqualTo("default");
        assertThat(submitted.categories()).hasSize(1);
        assertThat(submitted.categories().get(0).key()).isEqualTo("compensation");
        assertThat(submitted.categories().get(0).maxPoints()).isEqualTo(25);
        assertThat(submitted.categories().get(0).tiers()).hasSize(2);
        assertThat(submitted.categories().get(0).tiers().get(0).label()).isEqualTo("Excellent");
        assertThat(submitted.categories().get(0).tiers().get(0).points()).isEqualTo(25);
        assertThat(submitted.thresholds().applyImmediately()).isEqualTo(75);
        assertThat(submitted.thresholds().apply()).isEqualTo(55);
        assertThat(submitted.thresholds().considerOnly()).isEqualTo(35);
    }

    // 5. POST with apply <= consider re-renders form with validation error, no save.
    @Test
    @WithMockUser(username = "robsartin")
    void postInvalidThresholds_rerendersFormWithError() throws Exception {
        stubMembership("robsartin");

        mvc.perform(post("/envoy/rubrics/{name}", "default")
                        .with(csrf())
                        .param("categories[0].key", "compensation")
                        .param("categories[0].description", "Pay")
                        .param("categories[0].maxPoints", "25")
                        .param("categories[0].tiers[0].label", "Tier")
                        .param("categories[0].tiers[0].points", "10")
                        .param("categories[0].tiers[0].criteria", "x")
                        .param("thresholds.applyImmediately", "50")
                        .param("thresholds.apply", "60")
                        .param("thresholds.considerOnly", "30"))
                .andExpect(status().isOk())
                .andExpect(view().name("rubric-edit"))
                .andExpect(model().attributeHasErrors("form"));

        verify(rubrics, never()).saveNewVersion(any(), any(), any());
    }

    // 6. POST with zero categories re-renders with error.
    @Test
    @WithMockUser(username = "robsartin")
    void postZeroCategories_rerendersWithError() throws Exception {
        stubMembership("robsartin");

        mvc.perform(post("/envoy/rubrics/{name}", "default")
                        .with(csrf())
                        .param("thresholds.applyImmediately", "75")
                        .param("thresholds.apply", "55")
                        .param("thresholds.considerOnly", "35"))
                .andExpect(status().isOk())
                .andExpect(view().name("rubric-edit"))
                .andExpect(model().attributeHasErrors("form"));

        verify(rubrics, never()).saveNewVersion(any(), any(), any());
    }

    // 7. POST with a category that has zero tiers re-renders with error.
    @Test
    @WithMockUser(username = "robsartin")
    void postCategoryWithoutTiers_rerendersWithError() throws Exception {
        stubMembership("robsartin");

        mvc.perform(post("/envoy/rubrics/{name}", "default")
                        .with(csrf())
                        .param("categories[0].key", "compensation")
                        .param("categories[0].description", "Pay")
                        .param("categories[0].maxPoints", "25")
                        .param("thresholds.applyImmediately", "75")
                        .param("thresholds.apply", "55")
                        .param("thresholds.considerOnly", "35"))
                .andExpect(status().isOk())
                .andExpect(view().name("rubric-edit"))
                .andExpect(model().attributeHasErrors("form"));

        verify(rubrics, never()).saveNewVersion(any(), any(), any());
    }

    // 8. CSRF rejection: POST without token -> 403, no save.
    @Test
    @WithMockUser(username = "robsartin")
    void postWithoutCsrf_returns403AndDoesNotSave() throws Exception {
        stubMembership("robsartin");

        mvc.perform(post("/envoy/rubrics/{name}", "default")
                        .param("categories[0].key", "compensation")
                        .param("categories[0].description", "Pay")
                        .param("categories[0].maxPoints", "25")
                        .param("categories[0].tiers[0].label", "Tier")
                        .param("categories[0].tiers[0].points", "10")
                        .param("categories[0].tiers[0].criteria", "x")
                        .param("thresholds.applyImmediately", "75")
                        .param("thresholds.apply", "55")
                        .param("thresholds.considerOnly", "35"))
                .andExpect(status().isForbidden());

        verify(rubrics, never()).saveNewVersion(any(), any(), any());
    }

    // 9. Unauthenticated -> redirect to /login.
    @Test
    void unauthenticated_redirectsToLogin() throws Exception {
        mvc.perform(get("/envoy/rubrics"))
                .andExpect(status().is3xxRedirection());

        mvc.perform(get("/envoy/rubrics/default/edit"))
                .andExpect(status().is3xxRedirection());
    }
}
