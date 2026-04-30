package com.majordomo.adapter.in.event;

import com.majordomo.domain.model.AuditLogEntry;
import com.majordomo.domain.model.UuidFactory;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Typed registry of {@link AuditExtractor}s, keyed by event class. Replaces
 * the previous one-{@code @EventListener}-method-per-event-type pattern in
 * {@link AuditEventListener}: adding a new audited event type is now a
 * single registration line at startup.
 */
@Component
public class AuditExtractorRegistry {

    private final Map<Class<?>, AuditExtractor<?>> extractors = new HashMap<>();

    /**
     * Registers an extractor for an event class.
     *
     * @param eventType the event class
     * @param extractor the extractor that converts events of that class to {@link AuditExtraction}s
     * @param <E>       event type
     */
    public <E> void register(Class<E> eventType, AuditExtractor<E> extractor) {
        extractors.put(eventType, extractor);
    }

    /**
     * Looks up the extractor for the given event's class and produces an
     * {@link AuditLogEntry} ready to persist (id minted, fields populated).
     *
     * @param event the domain event
     * @return populated {@code AuditLogEntry}, or empty when no extractor is registered for that event type
     */
    @SuppressWarnings("unchecked")
    public Optional<AuditLogEntry> extract(Object event) {
        AuditExtractor<Object> extractor = (AuditExtractor<Object>) extractors.get(event.getClass());
        if (extractor == null) {
            return Optional.empty();
        }
        AuditExtraction data = extractor.extract(event);
        AuditLogEntry entry = new AuditLogEntry();
        entry.setId(UuidFactory.newId());
        entry.setOrganizationId(data.organizationId());
        entry.setEntityType(data.entityType());
        entry.setEntityId(data.entityId());
        entry.setAction(data.action());
        entry.setOccurredAt(data.occurredAt());
        return Optional.of(entry);
    }
}
