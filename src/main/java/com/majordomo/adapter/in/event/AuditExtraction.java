package com.majordomo.adapter.in.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Pure data emitted by an {@link AuditExtractor} — the audit-log row to write
 * for a given domain event. The {@link AuditExtractorRegistry} converts this
 * to an {@code AuditLogEntry} (mints the id, etc.).
 *
 * @param organizationId organization the event belongs to (may be null only
 *                       for events that legitimately lack an org)
 * @param entityType     audited entity type (e.g. "PROPERTY")
 * @param entityId       audited entity id
 * @param action         audited action (e.g. "CREATE", "ARCHIVE")
 * @param occurredAt     when the event occurred
 */
public record AuditExtraction(
        UUID organizationId,
        String entityType,
        UUID entityId,
        String action,
        Instant occurredAt
) { }
