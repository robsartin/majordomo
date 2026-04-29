package com.majordomo.adapter.in.web;

import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.application.identity.OrganizationAccessService;
import com.majordomo.domain.model.AuditLogEntry;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.port.out.AuditLogRepository;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;
import com.majordomo.domain.port.out.steward.PropertyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link AuditController}: query by entity + organization feed.
 */
@WebMvcTest(AuditController.class)
@Import(SecurityConfig.class)
class AuditControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean AuditLogRepository auditLogRepository;
    @MockitoBean PropertyRepository propertyRepository;
    @MockitoBean OrganizationAccessService organizationAccessService;
    @MockitoBean ApiKeyRepository apiKeyRepository;
    @MockitoBean OAuth2UserService oAuth2UserService;

    private static final UUID ORG_ID = UuidFactory.newId();

    /** GET /api/audit returns entries for the requested entity. */
    @Test
    @WithMockUser
    void queryByEntityReturnsEntries() throws Exception {
        UUID entityId = UuidFactory.newId();
        AuditLogEntry entry = entry("Property", entityId, "ARCHIVE",
                Instant.parse("2026-04-01T00:00:00Z"));
        when(auditLogRepository.findByEntityTypeAndEntityId("Property", entityId))
                .thenReturn(List.of(entry));

        mvc.perform(get("/api/audit")
                        .param("entityType", "Property")
                        .param("entityId", entityId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("ARCHIVE"))
                .andExpect(jsonPath("$[0].entityId").value(entityId.toString()));
    }

    /** GET /api/audit/organizations/{id} aggregates entries across the org's properties, sorted desc. */
    @Test
    @WithMockUser
    void organizationFeedAggregatesAndSortsDescending() throws Exception {
        UUID p1 = UuidFactory.newId();
        UUID p2 = UuidFactory.newId();
        Property prop1 = property(p1, ORG_ID);
        Property prop2 = property(p2, ORG_ID);
        when(propertyRepository.findByOrganizationId(ORG_ID))
                .thenReturn(List.of(prop1, prop2));

        AuditLogEntry older = entry("Property", p1, "CREATE",
                Instant.parse("2026-04-01T00:00:00Z"));
        AuditLogEntry newer = entry("Property", p2, "ARCHIVE",
                Instant.parse("2026-04-15T00:00:00Z"));
        when(auditLogRepository.findByEntityTypeAndEntityId("Property", p1))
                .thenReturn(List.of(older));
        when(auditLogRepository.findByEntityTypeAndEntityId("Property", p2))
                .thenReturn(List.of(newer));

        mvc.perform(get("/api/audit/organizations/{orgId}", ORG_ID))
                .andExpect(status().isOk())
                // Newer entry first.
                .andExpect(jsonPath("$[0].action").value("ARCHIVE"))
                .andExpect(jsonPath("$[1].action").value("CREATE"));
    }

    /** Cross-org access on the org feed returns 403. */
    @Test
    @WithMockUser
    void organizationFeedReturns403WhenAccessDenied() throws Exception {
        doThrow(new AccessDeniedException("denied"))
                .when(organizationAccessService).verifyAccess(ORG_ID);

        mvc.perform(get("/api/audit/organizations/{orgId}", ORG_ID))
                .andExpect(status().isForbidden());
    }

    private static AuditLogEntry entry(String entityType, UUID entityId,
                                       String action, Instant occurredAt) {
        AuditLogEntry e = new AuditLogEntry();
        e.setId(UuidFactory.newId());
        e.setEntityType(entityType);
        e.setEntityId(entityId);
        e.setAction(action);
        e.setOccurredAt(occurredAt);
        return e;
    }

    private static Property property(UUID id, UUID orgId) {
        Property p = new Property();
        p.setId(id);
        p.setOrganizationId(orgId);
        return p;
    }
}
