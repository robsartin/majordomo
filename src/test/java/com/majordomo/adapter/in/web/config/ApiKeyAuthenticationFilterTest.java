package com.majordomo.adapter.in.web.config;

import com.majordomo.domain.model.identity.ApiKey;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiKeyAuthenticationFilterTest {

    private ApiKeyRepository apiKeyRepository;
    private ApiKeyAuthenticationFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        apiKeyRepository = mock(ApiKeyRepository.class);
        filter = new ApiKeyAuthenticationFilter(apiKeyRepository);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validApiKeyAuthenticatesRequest() throws Exception {
        String rawKey = "mjd_abc123";
        String hashedKey = ApiKeyAuthenticationFilter.sha256(rawKey);
        UUID orgId = UUID.randomUUID();

        var apiKey = new ApiKey(UUID.randomUUID(), orgId, "test-key", hashedKey);
        apiKey.setCreatedAt(Instant.now());
        apiKey.setUpdatedAt(Instant.now());

        when(request.getHeader("X-API-Key")).thenReturn(rawKey);
        when(apiKeyRepository.findByHashedKey(hashedKey)).thenReturn(Optional.of(apiKey));

        filter.doFilterInternal(request, response, filterChain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals("apikey:" + orgId, auth.getPrincipal());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void expiredApiKeyIsRejected() throws Exception {
        String rawKey = "mjd_expired";
        String hashedKey = ApiKeyAuthenticationFilter.sha256(rawKey);

        var apiKey = new ApiKey(UUID.randomUUID(), UUID.randomUUID(), "expired-key", hashedKey);
        apiKey.setCreatedAt(Instant.now());
        apiKey.setUpdatedAt(Instant.now());
        apiKey.setExpiresAt(Instant.now().minusSeconds(3600));

        when(request.getHeader("X-API-Key")).thenReturn(rawKey);
        when(apiKeyRepository.findByHashedKey(hashedKey)).thenReturn(Optional.of(apiKey));

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void archivedApiKeyIsRejected() throws Exception {
        String rawKey = "mjd_archived";
        String hashedKey = ApiKeyAuthenticationFilter.sha256(rawKey);

        var apiKey = new ApiKey(UUID.randomUUID(), UUID.randomUUID(), "archived-key", hashedKey);
        apiKey.setCreatedAt(Instant.now());
        apiKey.setUpdatedAt(Instant.now());
        apiKey.setArchivedAt(Instant.now());

        when(request.getHeader("X-API-Key")).thenReturn(rawKey);
        when(apiKeyRepository.findByHashedKey(hashedKey)).thenReturn(Optional.of(apiKey));

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void missingHeaderPassesThrough() throws Exception {
        when(request.getHeader("X-API-Key")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void unknownKeyPassesThrough() throws Exception {
        String rawKey = "mjd_unknown";
        String hashedKey = ApiKeyAuthenticationFilter.sha256(rawKey);

        when(request.getHeader("X-API-Key")).thenReturn(rawKey);
        when(apiKeyRepository.findByHashedKey(hashedKey)).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void keyWithFutureExpirationAuthenticates() throws Exception {
        String rawKey = "mjd_future";
        String hashedKey = ApiKeyAuthenticationFilter.sha256(rawKey);
        UUID orgId = UUID.randomUUID();

        var apiKey = new ApiKey(UUID.randomUUID(), orgId, "future-key", hashedKey);
        apiKey.setCreatedAt(Instant.now());
        apiKey.setUpdatedAt(Instant.now());
        apiKey.setExpiresAt(Instant.now().plusSeconds(3600));

        when(request.getHeader("X-API-Key")).thenReturn(rawKey);
        when(apiKeyRepository.findByHashedKey(hashedKey)).thenReturn(Optional.of(apiKey));

        filter.doFilterInternal(request, response, filterChain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals("apikey:" + orgId, auth.getPrincipal());
    }
}
