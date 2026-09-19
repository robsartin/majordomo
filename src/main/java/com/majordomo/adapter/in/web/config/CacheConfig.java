package com.majordomo.adapter.in.web.config;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
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
@EnableCaching
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
        // Jackson 3: java.time and Optional are handled natively, so the
        // JavaTimeModule and Jdk8Module registrations Jackson 2 needed are gone.
        // Default typing moves onto the builder, and the Redis serializer is the
        // Jackson 3 one — GenericJackson2JsonRedisSerializer is Jackson 2 by name
        // and by binding.
        ObjectMapper mapper = JsonMapper.builder()
                .activateDefaultTyping(
                        tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator.builder()
                                .allowIfSubType(Object.class)
                                .build(),
                        tools.jackson.databind.DefaultTyping.NON_FINAL)
                .build();
        var jsonSerializer = new GenericJacksonJsonRedisSerializer(mapper);

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
