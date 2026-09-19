package com.majordomo.adapter.out.metadata.librarian;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Shared {@link RestClient} for book-metadata lookups.
 *
 * <p>Open Library asks callers to identify themselves; the User-Agent is set
 * here rather than per call so every request through this client carries it.
 */
@Configuration
public class MetadataHttpConfiguration {

    /**
     * Builds the shared metadata HTTP client.
     *
     * @return the configured {@link RestClient}
     */
    @Bean("metadataRestClient")
    public RestClient metadataRestClient() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(20).toMillis());
        return RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("User-Agent", "majordomo-librarian/1.0")
                .build();
    }
}
