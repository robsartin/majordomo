package com.majordomo.domain.model.identity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyTest {

    @Test
    void newKeyDefaultsToReadWriteScope() {
        var key = new ApiKey(UUID.randomUUID(), UUID.randomUUID(), "k", "hash");

        // Existing keys (and any minted without an explicit scope) are read-write,
        // matching the migration backfill so behaviour is unchanged by default.
        assertThat(key.getScope()).isEqualTo(ApiKeyScope.READ_WRITE);
    }

    @Test
    void scopeIsSettable() {
        var key = new ApiKey(UUID.randomUUID(), UUID.randomUUID(), "k", "hash");

        key.setScope(ApiKeyScope.READ_ONLY);

        assertThat(key.getScope()).isEqualTo(ApiKeyScope.READ_ONLY);
    }

    @Test
    void readOnlyScopeDoesNotPermitWrites() {
        assertThat(ApiKeyScope.READ_ONLY.permitsWrites()).isFalse();
        assertThat(ApiKeyScope.READ_WRITE.permitsWrites()).isTrue();
    }
}
