package com.majordomo.domain.port.out;

/**
 * Outbound port for publishing domain events.
 * Implementations may use Spring ApplicationEventPublisher, messaging, etc.
 */
public interface EventPublisher {

    /**
     * Publishes a domain event.
     *
     * @param event the event to publish
     */
    void publish(Object event);
}
