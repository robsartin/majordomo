package com.majordomo.adapter.in.web.audit;

import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.application.identity.CurrentOrganizationResolver;
import com.majordomo.domain.model.AuditLogEntry;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.port.out.AuditLogRepository;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;
import com.majordomo.domain.port.out.identity.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetails;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** Slice tests for the {@code /audit} log viewer (#242). */
@WebMvcTest(AuditPageController.class)
@Import(SecurityConfig.class)
class AuditPageControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean AuditLogRepository auditLogRepository;
    @MockitoBean UserRepository userRepository;
    @MockitoBean CurrentOrganizationResolver currentOrg;
    @MockitoBean ApiKeyRepository apiKeyRepository;
    @MockitoBean OAuth2UserService oAuth2UserService;

    private static final UUID ORG_ID = UuidFactory.newId();

    @BeforeEach
    void seedAuth() {
        User user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        when(currentOrg.resolve(any(UserDetails.class)))
                .thenReturn(new CurrentOrganizationResolver.Resolved(user, ORG_ID));
    }

    /** GET /audit returns 200 + audit view with rows for the user's org. */
    @Test
    @WithMockUser
    void listRendersAuditEntriesForOrg() throws Exception {
        UUID actorId = UuidFactory.newId();
        AuditLogEntry e1 = entry(ORG_ID, "PROPERTY", "ARCHIVE", actorId,
                Instant.parse("2026-04-01T10:00:00Z"));
        AuditLogEntry e2 = entry(ORG_ID, "JOB_POSTING", "CREATE", actorId,
                Instant.parse("2026-04-15T15:30:00Z"));
        when(auditLogRepository.find(eq(ORG_ID), isNull(), isNull(), isNull(), isNull(),
                org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(e2, e1));
        when(userRepository.findById(actorId))
                .thenReturn(Optional.of(new User(actorId, "robsartin", "rob@e.com")));

        MvcResult result = mvc.perform(get("/audit"))
                .andExpect(status().isOk())
                .andExpect(view().name("audit"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body)
                .contains("PROPERTY").contains("ARCHIVE")
                .contains("JOB_POSTING").contains("CREATE")
                .contains("robsartin");
    }

    /** Filter parameters propagate to the repository call. */
    @Test
    @WithMockUser
    void listForwardsFiltersToRepo() throws Exception {
        when(auditLogRepository.find(eq(ORG_ID), eq("PROPERTY"), any(),
                any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of());

        mvc.perform(get("/audit")
                        .param("entityType", "PROPERTY")
                        .param("since", "2026-01-01")
                        .param("until", "2026-12-31"))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(auditLogRepository).find(
                eq(ORG_ID),
                eq("PROPERTY"),
                isNull(),
                eq(Instant.parse("2026-01-01T00:00:00Z")),
                eq(Instant.parse("2026-12-31T00:00:00Z")),
                org.mockito.ArgumentMatchers.anyInt());
    }

    /** Empty state when no entries match. */
    @Test
    @WithMockUser
    void emptyStateRendersWhenNoEntries() throws Exception {
        when(auditLogRepository.find(eq(ORG_ID), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of());

        MvcResult result = mvc.perform(get("/audit"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getContentAsString())
                .contains("No audit entries");
    }

    /** No-org user redirects home. */
    @Test
    @WithMockUser
    void redirectHomeWhenNoOrg() throws Exception {
        User user = new User(UuidFactory.newId(), "lonely", "lonely@e.com");
        when(currentOrg.resolve(any(UserDetails.class)))
                .thenReturn(new CurrentOrganizationResolver.Resolved(user, null));

        mvc.perform(get("/audit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/"));
    }

    private static AuditLogEntry entry(UUID orgId, String entityType, String action,
                                       UUID userId, Instant occurredAt) {
        AuditLogEntry e = new AuditLogEntry();
        e.setId(UuidFactory.newId());
        e.setOrganizationId(orgId);
        e.setEntityType(entityType);
        e.setEntityId(UuidFactory.newId());
        e.setAction(action);
        e.setUserId(userId);
        e.setOccurredAt(occurredAt);
        return e;
    }
}
