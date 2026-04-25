package com.majordomo.adapter.in.web.config;

import com.majordomo.domain.model.DashboardSummary;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Redis cache uses JSON serialization, so plain records without
 * {@code implements Serializable} round-trip cleanly. Regression test for #125.
 */
class CacheConfigTest {

    private final CacheConfig config = new CacheConfig();

    @Test
    void serializesNonSerializableRecord() {
        RedisCacheConfiguration cacheConfig =
                config.redisCacheConfiguration(300000L, "majordomo:");

        DashboardSummary summary = new DashboardSummary(
                3, 7, List.of(), List.of(), List.of(), new BigDecimal("123.45"));

        ByteBuffer buf = cacheConfig.getValueSerializationPair().write(summary);
        byte[] bytes = new byte[buf.remaining()];
        buf.get(bytes);

        assertThat(bytes).isNotEmpty();
        String json = new String(bytes, StandardCharsets.UTF_8);
        assertThat(json).contains("\"propertyCount\"").contains("123.45");
    }

    @Test
    void appliesKeyPrefix() {
        RedisCacheConfiguration cacheConfig =
                config.redisCacheConfiguration(300000L, "majordomo:");

        String fullKey = cacheConfig.getKeyPrefixFor("dashboard");

        assertThat(fullKey).startsWith("majordomo:").contains("dashboard");
    }
}
