package com.majordomo.adapter.out.notification;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Notification adapter with circuit breaker and retry protection.
 * Currently logs notifications; will send real emails when SMTP is configured.
 */
@Component
public class NotificationAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationAdapter.class);

    /**
     * Sends a notification with circuit breaker and retry protection.
     *
     * @param to      the recipient
     * @param subject the notification subject
     * @param body    the notification body
     */
    @CircuitBreaker(name = "notification", fallbackMethod = "sendFallback")
    @Retry(name = "notification")
    public void send(String to, String subject, String body) {
        LOG.info("Notification to={} subject={}", to, subject);
        // Future: delegate to email sender
    }

    /**
     * Fallback when notification sending fails.
     *
     * @param to      the recipient
     * @param subject the notification subject
     * @param body    the notification body
     * @param ex      the cause of failure
     */
    public void sendFallback(String to, String subject, String body, Exception ex) {
        LOG.warn("Notification failed (circuit breaker), will retry later: to={} subject={} error={}",
                to, subject, ex.getMessage());
    }
}
