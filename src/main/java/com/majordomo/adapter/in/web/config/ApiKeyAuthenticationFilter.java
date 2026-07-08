package com.majordomo.adapter.in.web.config;

import com.majordomo.domain.model.identity.ApiKey;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Authenticates requests bearing an {@code X-API-Key} header by looking up
 * the SHA-256 hash of the provided key in the API key store.
 *
 * <p>Uses SHA-256 (not Argon2id) for fast O(1) lookup on every request.
 * The raw key is never stored; only its SHA-256 hash is persisted.</p>
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";
    private final ApiKeyRepository apiKeyRepository;

    /**
     * Constructs the filter with the API key repository.
     *
     * @param apiKeyRepository the outbound port for API key lookups
     */
    public ApiKeyAuthenticationFilter(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String rawKey = request.getHeader(API_KEY_HEADER);
        if (rawKey != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            var found = apiKeyRepository.findByHashedKey(sha256(rawKey));
            if (found.isPresent() && isActive(found.get())) {
                var apiKey = found.get();
                if (!apiKey.getScope().permitsWrites() && isWriteMethod(request.getMethod())) {
                    // Read-only key attempting a state-changing request: reject with
                    // 403 and stop — do not authenticate, do not proceed down the chain.
                    response.sendError(HttpServletResponse.SC_FORBIDDEN,
                            "API key is read-only and cannot perform write operations");
                    return;
                }
                var auth = new UsernamePasswordAuthenticationToken(
                        "apikey:" + apiKey.getOrganizationId(), null, List.of());
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        filterChain.doFilter(request, response);
    }

    private static boolean isActive(ApiKey apiKey) {
        return apiKey.getArchivedAt() == null
                && (apiKey.getExpiresAt() == null || apiKey.getExpiresAt().isAfter(Instant.now()));
    }

    private static boolean isWriteMethod(String method) {
        if (method == null) {
            return false;
        }
        return switch (method.toUpperCase(Locale.ROOT)) {
            case "POST", "PUT", "PATCH", "DELETE" -> true;
            default -> false;
        };
    }

    /**
     * Computes the SHA-256 hex digest of the given input string.
     *
     * @param input the string to hash
     * @return the lowercase hex-encoded SHA-256 digest
     */
    public static String sha256(String input) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
