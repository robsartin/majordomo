package com.majordomo.adapter.out.persistence.identity;

import com.majordomo.IntegrationTest;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.identity.ApiKey;
import com.majordomo.domain.model.identity.ApiKeyScope;
import com.majordomo.domain.model.identity.Organization;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;
import com.majordomo.domain.port.out.identity.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Postgres round-trip for API key scope + last-used tracking (#293): scope
 * persists, the DEFAULT backfills rows written without a scope, and
 * touchLastUsed updates only the timestamp.
 */
@IntegrationTest
class ApiKeyScopeIntegrationTest {

    @Autowired
    private ApiKeyRepository apiKeys;

    @Autowired
    private OrganizationRepository organizations;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID newOrg() {
        UUID id = UuidFactory.newId();
        organizations.save(new Organization(id, "org-" + id));
        return id;
    }

    private ApiKey key(UUID orgId, ApiKeyScope scope) {
        var now = Instant.now();
        ApiKey k = new ApiKey(UuidFactory.newId(), orgId, "k-" + UuidFactory.newId(), "hash-" + orgId);
        k.setScope(scope);
        k.setCreatedAt(now);
        k.setUpdatedAt(now);
        return k;
    }

    @Test
    void scopePersistsAndReloads() {
        UUID org = newOrg();
        ApiKey saved = apiKeys.save(key(org, ApiKeyScope.READ_ONLY));

        assertThat(apiKeys.findById(saved.getId()).orElseThrow().getScope())
                .isEqualTo(ApiKeyScope.READ_ONLY);
    }

    @Test
    void rowInsertedWithoutScopeDefaultsToReadWrite() {
        UUID org = newOrg();
        UUID id = UuidFactory.newId();
        // Simulate a pre-migration row: insert without specifying scope so the
        // column DEFAULT ('READ_WRITE') backfills it.
        jdbc.update("""
                INSERT INTO api_keys (id, organization_id, name, hashed_key, created_at, updated_at)
                VALUES (?, ?, 'legacy', ?, now(), now())
                """, id, org, "legacy-hash-" + id);

        assertThat(apiKeys.findById(id).orElseThrow().getScope())
                .isEqualTo(ApiKeyScope.READ_WRITE);
    }

    @Test
    void touchLastUsedUpdatesTimestamp() {
        UUID org = newOrg();
        ApiKey saved = apiKeys.save(key(org, ApiKeyScope.READ_WRITE));
        assertThat(saved.getLastUsedAt()).isNull();

        Instant used = Instant.parse("2026-07-07T12:00:00Z");
        apiKeys.touchLastUsed(saved.getId(), used);

        assertThat(apiKeys.findById(saved.getId()).orElseThrow().getLastUsedAt())
                .isEqualTo(used);
    }
}
