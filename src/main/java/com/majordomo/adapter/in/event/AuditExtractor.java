package com.majordomo.adapter.in.event;

/**
 * Converts a domain event into an {@link AuditExtraction} describing the
 * audit-log row to write. Implementations are typically lambdas registered
 * with {@link AuditExtractorRegistry} at startup.
 *
 * @param <E> the event type this extractor handles
 */
@FunctionalInterface
public interface AuditExtractor<E> {

    /**
     * @param event the domain event
     * @return the audit-row metadata to persist
     */
    AuditExtraction extract(E event);
}
