package com.majordomo.adapter.in.web.envoy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.application.identity.OrganizationAccessService;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.Category;
import com.majordomo.domain.model.envoy.Rubric;
import com.majordomo.domain.model.envoy.Thresholds;
import com.majordomo.domain.model.envoy.Tier;
import com.majordomo.domain.port.in.envoy.ManageRubricUseCase;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;
import com.majordomo.domain.port.out.identity.MembershipRepository;
import com.majordomo.domain.port.out.identity.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RubricController.class)
@Import(SecurityConfig.class)
class RubricControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockitoBean ManageRubricUseCase rubrics;
    @MockitoBean OrganizationAccessService organizationAccessService;
    @MockitoBean UserRepository userRepository;
    @MockitoBean MembershipRepository membershipRepository;
    @MockitoBean ApiKeyRepository apiKeyRepository;
    @MockitoBean OAuth2UserService oAuth2UserService;

    private static final UUID ORG_ID = UUID.randomUUID();

    @Test
    @WithMockUser
    void putRubric_createsNewVersion() throws Exception {
        var saved = new Rubric(UuidFactory.newId(), Optional.of(ORG_ID), 4, "default",
                List.of(),
                List.of(new Category("c", "x", 10, List.of(new Tier("Only", 5, "x")))),
                List.of(), new Thresholds(20, 15, 5), Instant.now());
        doNothing().when(organizationAccessService).verifyAccess(any());
        when(rubrics.saveNewVersion(eq("default"), any(), eq(ORG_ID))).thenReturn(saved);

        mvc.perform(put("/api/envoy/rubrics/default")
                        .param("organizationId", ORG_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(saved)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(4));
    }
}
