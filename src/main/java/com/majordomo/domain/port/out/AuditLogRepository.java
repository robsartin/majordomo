package com.majordomo.domain.port.out;

import com.majordomo.domain.model.AuditLogEntry;

import java.time.Instant;
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

    /**
     * Queries entries scoped to an organization, with optional filters. Each
     * filter is independent — pass {@code null} to skip. Results are ordered
     * by {@code occurred_at} descending and capped at {@code limit}.
     *
     * @param organizationId required organization scope
     * @param entityType     optional entity-type filter (e.g. "PROPERTY")
     * @param userId         optional actor filter
     * @param since          optional inclusive lower bound on occurred_at
     * @param until          optional exclusive upper bound on occurred_at
     * @param limit          maximum rows to return
     * @return matching entries
     */
    List<AuditLogEntry> find(UUID organizationId, String entityType, UUID userId,
                             Instant since, Instant until, int limit);
}
