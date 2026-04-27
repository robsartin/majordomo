package com.majordomo.adapter.in.web.envoy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.adapter.in.web.envoy.dto.IngestPostingRequest;
import com.majordomo.application.identity.OrganizationAccessService;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.JobSourceRequest;
import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.port.in.envoy.IngestJobPostingUseCase;
import com.majordomo.domain.port.in.envoy.ScoreJobPostingUseCase;
import com.majordomo.domain.port.out.envoy.JobPostingRepository;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostingController.class)
@Import(SecurityConfig.class)
class PostingControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockitoBean IngestJobPostingUseCase ingestUseCase;
    @MockitoBean ScoreJobPostingUseCase scoreUseCase;
    @MockitoBean JobPostingRepository jobPostingRepository;
    @MockitoBean OrganizationAccessService organizationAccessService;

    // Required by SecurityConfig
    @MockitoBean UserRepository userRepository;
    @MockitoBean MembershipRepository membershipRepository;
    @MockitoBean ApiKeyRepository apiKeyRepository;
    @MockitoBean OAuth2UserService oAuth2UserService;

    private static final UUID ORG_ID = UUID.randomUUID();

    @Test
    @WithMockUser
    void ingestReturns201WithLocation() throws Exception {
        var saved = new JobPosting();
        saved.setId(UuidFactory.newId());
        saved.setOrganizationId(ORG_ID);
        saved.setSource("manual");
        saved.setRawText("body");
        doNothing().when(organizationAccessService).verifyAccess(any());
        when(ingestUseCase.ingest(any(JobSourceRequest.class), eq(ORG_ID))).thenReturn(saved);

        var body = new IngestPostingRequest("manual", "body", Map.of());
        mvc.perform(post("/api/envoy/postings")
                        .param("organizationId", ORG_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"));
    }

    @Test
    @WithMockUser
    void rescoreAllReturnsCountAndScoresEachPosting() throws Exception {
        var p1 = new JobPosting();
        p1.setId(UuidFactory.newId());
        p1.setOrganizationId(ORG_ID);
        p1.setSource("manual");
        p1.setRawText("body 1");
        var p2 = new JobPosting();
        p2.setId(UuidFactory.newId());
        p2.setOrganizationId(ORG_ID);
        p2.setSource("manual");
        p2.setRawText("body 2");

        doNothing().when(organizationAccessService).verifyAccess(any());
        when(jobPostingRepository.findAllByOrganizationId(ORG_ID)).thenReturn(List.of(p1, p2));
        var report = new ScoreReport(UuidFactory.newId(), ORG_ID, p1.getId(),
                UuidFactory.newId(), 1, Optional.empty(),
                List.of(), List.of(), 70, 70, Recommendation.APPLY,
                "claude-sonnet-4-6", Instant.now());
        when(scoreUseCase.score(any(), eq("default"), eq(ORG_ID))).thenReturn(report);

        mvc.perform(post("/api/envoy/postings/rescore")
                        .param("organizationId", ORG_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));

        org.mockito.Mockito.verify(scoreUseCase)
                .score(eq(p1.getId()), eq("default"), eq(ORG_ID));
        org.mockito.Mockito.verify(scoreUseCase)
                .score(eq(p2.getId()), eq("default"), eq(ORG_ID));
    }

    @Test
    @WithMockUser
    void rescoreAllWithCustomRubricNameUsesIt() throws Exception {
        var p1 = new JobPosting();
        p1.setId(UuidFactory.newId());
        p1.setOrganizationId(ORG_ID);
        p1.setSource("manual");
        p1.setRawText("body 1");

        doNothing().when(organizationAccessService).verifyAccess(any());
        when(jobPostingRepository.findAllByOrganizationId(ORG_ID)).thenReturn(List.of(p1));
        var report = new ScoreReport(UuidFactory.newId(), ORG_ID, p1.getId(),
                UuidFactory.newId(), 1, Optional.empty(),
                List.of(), List.of(), 70, 70, Recommendation.APPLY,
                "claude-sonnet-4-6", Instant.now());
        when(scoreUseCase.score(any(), eq("strict"), eq(ORG_ID))).thenReturn(report);

        mvc.perform(post("/api/envoy/postings/rescore")
                        .param("organizationId", ORG_ID.toString())
                        .param("rubricName", "strict"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        org.mockito.Mockito.verify(scoreUseCase)
                .score(eq(p1.getId()), eq("strict"), eq(ORG_ID));
    }

    @Test
    @WithMockUser
    void scoreReturnsReport() throws Exception {
        var postingId = UuidFactory.newId();
        var report = new ScoreReport(UuidFactory.newId(), ORG_ID, postingId,
                UuidFactory.newId(), 1, Optional.empty(),
                List.of(), List.of(), 60, 60, Recommendation.APPLY,
                "claude-sonnet-4-6", Instant.now());
        doNothing().when(organizationAccessService).verifyAccess(any());
        when(scoreUseCase.score(eq(postingId), eq("default"), eq(ORG_ID))).thenReturn(report);

        mvc.perform(post("/api/envoy/postings/" + postingId + "/score")
                        .param("organizationId", ORG_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalScore").value(60))
                .andExpect(jsonPath("$.recommendation").value("APPLY"));
    }
}
