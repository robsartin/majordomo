package com.majordomo.domain.port.out.herald;

/**
 * Outbound port for sending notifications.
 */
public interface NotificationPort {

    /**
     * Sends a notification.
     *
     * @param to      the recipient
     * @param subject the subject line
     * @param body    the message body
     */
    void send(String to, String subject, String body);
}
