package com.majordomo.adapter.out.ingest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Shared {@link RestClient} used by all HTTP-based {@code JobSource} implementations.
 * Conservative timeouts — job boards can be slow, but we never want a single
 * posting fetch to block a thread for minutes.
 */
@Configuration
public class IngestHttpConfiguration {

    /**
     * Builds the shared HTTP client (qualifier {@code ingestRestClient}) for use
     * in job sources.
     *
     * @return the configured {@link RestClient}
     */
    @Bean("ingestRestClient")
    public RestClient ingestRestClient() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(30).toMillis());
        return RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("User-Agent", "majordomo-envoy/1.0")
                .build();
    }
}
