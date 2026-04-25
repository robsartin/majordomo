package com.majordomo.adapter.in.web.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis cache configuration. Replaces the default JDK serializer (which requires
 * cached value types to implement {@code Serializable}) with a Jackson-based
 * JSON serializer, so plain records like {@code DashboardSummary} cache without
 * needing to declare {@code Serializable} on every type in the value graph.
 *
 * <p>Defaults pulled from {@code spring.cache.redis.*} properties (TTL, key prefix).</p>
 */
@Configuration
public class CacheConfig {

    /**
     * Builds the {@link RedisCacheConfiguration} bean used by Spring Boot's
     * auto-configured {@code RedisCacheManager}. Setting this bean overrides
     * the default JDK serialization without disabling auto-configuration.
     *
     * @param ttlMillis  cache entry TTL in milliseconds (from
     *                   {@code spring.cache.redis.time-to-live})
     * @param keyPrefix  prefix prepended to every cache key (from
     *                   {@code spring.cache.redis.key-prefix})
     * @return a {@link RedisCacheConfiguration} with JSON value serialization
     */
    @Bean
    public RedisCacheConfiguration redisCacheConfiguration(
            @Value("${spring.cache.redis.time-to-live:300000}") long ttlMillis,
            @Value("${spring.cache.redis.key-prefix:}") String keyPrefix) {
        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .addModule(new Jdk8Module())
                .build();
        mapper.activateDefaultTyping(
                mapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL);
        var jsonSerializer = new GenericJackson2JsonRedisSerializer(mapper);

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMillis(ttlMillis))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jsonSerializer));
        if (keyPrefix != null && !keyPrefix.isBlank()) {
            config = config.computePrefixWith(name -> keyPrefix + name + "::");
        }
        return config;
    }
}
