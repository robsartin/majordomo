package com.majordomo.adapter.in.web.identity;

import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.identity.ApiKey;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link ApiKeyController}: create / list / revoke under
 * {@code /api/organizations/{orgId}/api-keys}.
 */
@WebMvcTest(ApiKeyController.class)
@Import(SecurityConfig.class)
class ApiKeyControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean ApiKeyRepository apiKeyRepository;
    @MockitoBean OAuth2UserService oAuth2UserService;

    private static final UUID ORG_ID = UuidFactory.newId();

    /** POST /api/organizations/{orgId}/api-keys returns 201 with the plaintext key + metadata. */
    @Test
    @WithMockUser
    void createReturns201WithPlaintextKey() throws Exception {
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(post("/api/organizations/{orgId}/api-keys", ORG_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"CI deploy key"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.organizationId").value(ORG_ID.toString()))
                .andExpect(jsonPath("$.name").value("CI deploy key"))
                .andExpect(jsonPath("$.key").value(org.hamcrest.Matchers.startsWith("mjd_")));

        verify(apiKeyRepository).save(any(ApiKey.class));
    }

    /** POST creates with an expiration when supplied. */
    @Test
    @WithMockUser
    void createPersistsExpiresAt() throws Exception {
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> {
            ApiKey k = inv.getArgument(0);
            // The controller already set createdAt; assert expiresAt round-trips.
            return k;
        });

        mvc.perform(post("/api/organizations/{orgId}/api-keys", ORG_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"short-lived","expiresAt":"2026-12-31T00:00:00Z"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresAt").value("2026-12-31T00:00:00Z"));
    }

    /** GET lists active keys (archived excluded), no plaintext. */
    @Test
    @WithMockUser
    void listReturnsActiveKeysOnly() throws Exception {
        ApiKey active = key(ORG_ID, "active", null);
        ApiKey archived = key(ORG_ID, "archived", Instant.parse("2026-01-01T00:00:00Z"));
        when(apiKeyRepository.findByOrganizationId(ORG_ID)).thenReturn(List.of(active, archived));

        mvc.perform(get("/api/organizations/{orgId}/api-keys", ORG_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("active"))
                .andExpect(jsonPath("$[0].id").value(active.getId().toString()))
                // No plaintext "key" field in list responses.
                .andExpect(jsonPath("$[0].key").doesNotExist());
    }

    /** DELETE revokes by setting archived_at, returns 204. */
    @Test
    @WithMockUser
    void revokeReturns204AndArchives() throws Exception {
        ApiKey existing = key(ORG_ID, "to-revoke", null);
        when(apiKeyRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(delete("/api/organizations/{orgId}/api-keys/{id}",
                        ORG_ID, existing.getId()).with(csrf()))
                .andExpect(status().isNoContent());

        verify(apiKeyRepository).save(any(ApiKey.class));
    }

    /** DELETE returns 404 when no matching key in this org. */
    @Test
    @WithMockUser
    void revokeReturns404WhenMissing() throws Exception {
        UUID id = UuidFactory.newId();
        when(apiKeyRepository.findById(id)).thenReturn(Optional.empty());

        mvc.perform(delete("/api/organizations/{orgId}/api-keys/{id}", ORG_ID, id).with(csrf()))
                .andExpect(status().isNotFound());

        verify(apiKeyRepository, never()).save(any());
    }

    /** DELETE returns 404 when key exists but belongs to a different org. */
    @Test
    @WithMockUser
    void revokeReturns404WhenWrongOrg() throws Exception {
        UUID otherOrg = UuidFactory.newId();
        ApiKey foreign = key(otherOrg, "foreign", null);
        when(apiKeyRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        mvc.perform(delete("/api/organizations/{orgId}/api-keys/{id}",
                        ORG_ID, foreign.getId()).with(csrf()))
                .andExpect(status().isNotFound());

        verify(apiKeyRepository, never()).save(any());
    }

    private static ApiKey key(UUID orgId, String name, Instant archivedAt) {
        ApiKey k = new ApiKey(UuidFactory.newId(), orgId, name, "hash");
        k.setCreatedAt(Instant.parse("2026-04-01T00:00:00Z"));
        k.setUpdatedAt(Instant.parse("2026-04-01T00:00:00Z"));
        k.setArchivedAt(archivedAt);
        return k;
    }
}
