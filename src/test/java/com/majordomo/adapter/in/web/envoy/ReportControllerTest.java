package com.majordomo.adapter.in.web.envoy;

import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.application.identity.OrganizationAccessService;
import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.envoy.ScoreReport;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportController.class)
@Import(SecurityConfig.class)
class ReportControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean QueryScoreReportsUseCase query;
    @MockitoBean OrganizationAccessService organizationAccessService;
    @MockitoBean UserRepository userRepository;
    @MockitoBean MembershipRepository membershipRepository;
    @MockitoBean ApiKeyRepository apiKeyRepository;
    @MockitoBean OAuth2UserService oAuth2UserService;

    private static final UUID ORG_ID = UUID.randomUUID();

    @Test
    @WithMockUser
    void listPassesFiltersThrough() throws Exception {
        doNothing().when(organizationAccessService).verifyAccess(any());
        when(query.query(eq(ORG_ID), any(), any(), any(), any(Integer.class)))
                .thenReturn(new Page<>(List.of(), null, false));

        mvc.perform(get("/api/envoy/reports")
                        .param("organizationId", ORG_ID.toString())
                        .param("minFinalScore", "70")
                        .param("recommendation", "APPLY_NOW")
                        .param("limit", "25"))
                .andExpect(status().isOk());

        verify(query).query(eq(ORG_ID), eq(70), eq(Recommendation.APPLY_NOW), eq(null), eq(25));
    }

    @Test
    @WithMockUser
    void getByIdReturns200() throws Exception {
        var id = UuidFactory.newId();
        var report = new ScoreReport(id, ORG_ID, UuidFactory.newId(), UuidFactory.newId(), 1,
                Optional.empty(), List.of(), List.of(), 10, 10,
                Recommendation.CONSIDER, "m", Instant.now());
        doNothing().when(organizationAccessService).verifyAccess(any());
        when(query.findById(id, ORG_ID)).thenReturn(Optional.of(report));

        mvc.perform(get("/api/envoy/reports/" + id)
                        .param("organizationId", ORG_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    @WithMockUser
    void getByIdReturns404WhenMissing() throws Exception {
        doNothing().when(organizationAccessService).verifyAccess(any());
        when(query.findById(any(), any())).thenReturn(Optional.empty());

        mvc.perform(get("/api/envoy/reports/" + UuidFactory.newId())
                        .param("organizationId", ORG_ID.toString()))
                .andExpect(status().isNotFound());
    }
}
