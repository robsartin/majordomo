package com.majordomo.adapter.out.event;

import com.majordomo.domain.port.out.EventPublisher;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Publishes domain events via Spring's {@link ApplicationEventPublisher}.
 */
@Component
public class SpringEventPublisher implements EventPublisher {

    private final ApplicationEventPublisher publisher;

    /**
     * Constructs the adapter.
     *
     * @param publisher Spring's event publisher
     */
    public SpringEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(Object event) {
        publisher.publishEvent(event);
    }
}
