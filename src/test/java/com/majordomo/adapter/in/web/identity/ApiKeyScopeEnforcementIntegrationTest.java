package com.majordomo.adapter.in.web.identity;

import com.majordomo.IntegrationTest;
import com.majordomo.adapter.in.web.config.ApiKeyAuthenticationFilter;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.identity.ApiKey;
import com.majordomo.domain.model.identity.ApiKeyScope;
import com.majordomo.domain.model.identity.Organization;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;
import com.majordomo.domain.port.out.identity.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end proof, through the real Spring Security chain, that API key scope
 * is enforced (#293): a read-only key authenticates reads but is rejected with
 * 403 on writes, while a read-write key can write. Runs against Testcontainers
 * Postgres so the key is looked up from a real store.
 */
@IntegrationTest
@AutoConfigureMockMvc
class ApiKeyScopeEnforcementIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApiKeyRepository apiKeys;

    @Autowired
    private OrganizationRepository organizations;

    private UUID seedOrg() {
        UUID id = UuidFactory.newId();
        organizations.save(new Organization(id, "org-" + id));
        return id;
    }

    private String seedKey(UUID orgId, String rawKey, ApiKeyScope scope) {
        var now = Instant.now();
        ApiKey key = new ApiKey(UuidFactory.newId(), orgId, "k",
                ApiKeyAuthenticationFilter.sha256(rawKey));
        key.setScope(scope);
        key.setCreatedAt(now);
        key.setUpdatedAt(now);
        apiKeys.save(key);
        return rawKey;
    }

    @Test
    void readOnlyKeyCanReadButNotWrite() throws Exception {
        UUID org = seedOrg();
        String raw = seedKey(org, "mjd_ro_" + org, ApiKeyScope.READ_ONLY);

        // Read is allowed (authenticated) — not 401/403.
        mvc.perform(get("/api/organizations/{orgId}/api-keys", org)
                        .header("X-API-Key", raw))
                .andExpect(status().isOk());

        // Write is rejected with 403 before reaching the controller.
        mvc.perform(post("/api/organizations/{orgId}/api-keys", org)
                        .header("X-API-Key", raw)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"blocked\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void readWriteKeyCanWrite() throws Exception {
        UUID org = seedOrg();
        String raw = seedKey(org, "mjd_rw_" + org, ApiKeyScope.READ_WRITE);

        mvc.perform(post("/api/organizations/{orgId}/api-keys", org)
                        .header("X-API-Key", raw)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"allowed\"}"))
                .andExpect(status().isCreated());
    }
}
