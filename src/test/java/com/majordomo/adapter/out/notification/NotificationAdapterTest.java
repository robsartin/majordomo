package com.majordomo.adapter.out.notification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Unit tests for {@link NotificationAdapter}.
 *
 * <p>Note: Circuit breaker and retry behavior require a Spring integration test
 * context because Resilience4j uses AOP proxies. These unit tests verify the
 * adapter's direct method behavior without the proxy layer.</p>
 */
class NotificationAdapterTest {

    private final NotificationAdapter adapter = new NotificationAdapter();

    /**
     * Verifies that send completes without throwing.
     */
    @Test
    void sendLogsSuccessfully() {
        assertDoesNotThrow(() -> adapter.send("user@example.com", "Test Subject", "Test Body"));
    }

    /**
     * Verifies that the fallback method completes without throwing.
     */
    @Test
    void sendFallbackLogsWarning() {
        assertDoesNotThrow(() ->
                adapter.sendFallback("user@example.com", "Test Subject", "Test Body",
                        new RuntimeException("connection refused")));
    }
}
