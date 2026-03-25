package com.majordomo.domain.port.out;

import com.majordomo.domain.model.AuditLogEntry;

import java.util.List;
import java.util.UUID;

/**
 * Outbound port for persisting and querying audit log entries.
 */
public interface AuditLogRepository {

    /**
     * Persists an audit log entry.
     *
     * @param entry the audit log entry to save
     * @return the saved entry
     */
    AuditLogEntry save(AuditLogEntry entry);

    /**
     * Returns all audit log entries for a given entity type and entity ID,
     * ordered by occurred_at descending.
     *
     * @param entityType the type of entity (e.g. "ServiceRecord", "Property")
     * @param entityId   the entity ID
     * @return list of audit log entries, or an empty list if none exist
     */
    List<AuditLogEntry> findByEntityTypeAndEntityId(String entityType, UUID entityId);

    /**
     * Returns all audit log entries performed by a given user,
     * ordered by occurred_at descending.
     *
     * @param userId the user ID
     * @return list of audit log entries, or an empty list if none exist
     */
    List<AuditLogEntry> findByUserId(UUID userId);
}
