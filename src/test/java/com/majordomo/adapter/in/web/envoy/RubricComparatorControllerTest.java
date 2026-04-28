package com.majordomo.adapter.in.web.envoy;

import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.envoy.RubricComparison;
import com.majordomo.domain.model.identity.Membership;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.port.in.envoy.CompareRubricVersionsUseCase;
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

import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Tests for {@link RubricComparatorController}.
 */
@WebMvcTest(RubricComparatorController.class)
@Import(SecurityConfig.class)
class RubricComparatorControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean CompareRubricVersionsUseCase comparisonUseCase;
    @MockitoBean UserRepository userRepository;
    @MockitoBean MembershipRepository membershipRepository;
    @MockitoBean ApiKeyRepository apiKeyRepository;
    @MockitoBean OAuth2UserService oAuth2UserService;

    private static final UUID ORG_ID = UuidFactory.newId();

    /** Renders the rubric-compare template populated from the use case result. */
    @Test
    @WithMockUser(username = "robsartin")
    void rendersComparisonPage() throws Exception {
        var user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        var membership = new Membership();
        membership.setUserId(user.getId());
        membership.setOrganizationId(ORG_ID);
        when(userRepository.findByUsername("robsartin")).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserId(user.getId())).thenReturn(List.of(membership));

        RubricComparison result = new RubricComparison(
                "default", 1, 2, List.of(),
                75.0, 80.0, new EnumMap<>(Recommendation.class), new EnumMap<>(Recommendation.class), 0L);
        when(comparisonUseCase.compare("default", 1, 2, 10, ORG_ID)).thenReturn(result);

        mvc.perform(get("/envoy/rubrics/{name}/compare", "default")
                        .param("from", "1")
                        .param("to", "2"))
                .andExpect(status().isOk())
                .andExpect(view().name("rubric-compare"))
                .andExpect(model().attribute("rubricName", "default"))
                .andExpect(model().attribute("fromVersion", 1))
                .andExpect(model().attribute("toVersion", 2))
                .andExpect(model().attribute("limit", 10))
                .andExpect(model().attribute("result", result));

        verify(comparisonUseCase).compare("default", 1, 2, 10, ORG_ID);
    }

    /** Limit query parameter is clamped to 50 max. */
    @Test
    @WithMockUser(username = "robsartin")
    void clampsLimitParameter() throws Exception {
        var user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        var membership = new Membership();
        membership.setUserId(user.getId());
        membership.setOrganizationId(ORG_ID);
        when(userRepository.findByUsername("robsartin")).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserId(user.getId())).thenReturn(List.of(membership));

        RubricComparison result = new RubricComparison(
                "default", 1, 2, List.of(),
                0.0, 0.0, new EnumMap<>(Recommendation.class), new EnumMap<>(Recommendation.class), 0L);
        when(comparisonUseCase.compare(eq("default"), eq(1), eq(2), eq(50), eq(ORG_ID))).thenReturn(result);

        mvc.perform(get("/envoy/rubrics/{name}/compare", "default")
                        .param("from", "1").param("to", "2").param("limit", "9999"))
                .andExpect(status().isOk());

        verify(comparisonUseCase).compare("default", 1, 2, 50, ORG_ID);
    }

    /** Unauthenticated request redirects to login. */
    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/envoy/rubrics/{name}/compare", "default")
                        .param("from", "1").param("to", "2"))
                .andExpect(status().is3xxRedirection());
        verify(comparisonUseCase, never()).compare(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any());
    }
}
