package com.majordomo.adapter.out.interestgraph.librarian;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Wiring for the Segue interest-graph client.
 *
 * <p>The default endpoint is loopback because that is the only place Segue
 * listens: it binds to 127.0.0.1 and has no authentication, so reaching it from
 * anywhere else is a deliberate change on Segue's side with its own security
 * review, not something majordomo can configure its way into.
 */
@Configuration
public class InterestGraphConfiguration {

    /**
     * Builds the MCP client for Segue.
     *
     * @param endpoint Segue's MCP endpoint ({@code librarian.segue.endpoint})
     * @return the client
     */
    @Bean
    public SegueMcpClient segueMcpClient(
            @Value("${librarian.segue.endpoint:http://127.0.0.1:8080/mcp}") String endpoint) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(30).toMillis());
        return new SegueMcpClient(
                RestClient.builder().requestFactory(factory).build(), endpoint);
    }
}
