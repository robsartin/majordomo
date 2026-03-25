package com.majordomo.adapter.out.persistence.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link AuditLogEntity}.
 */
public interface JpaAuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {

    /**
     * Returns audit log entries for a given entity, ordered by occurred_at descending.
     *
     * @param entityType the entity type
     * @param entityId   the entity ID
     * @return list of audit log entities
     */
    List<AuditLogEntity> findByEntityTypeAndEntityIdOrderByOccurredAtDesc(
            String entityType, UUID entityId);

    /**
     * Returns audit log entries for a given user, ordered by occurred_at descending.
     *
     * @param userId the user ID
     * @return list of audit log entities
     */
    List<AuditLogEntity> findByUserIdOrderByOccurredAtDesc(UUID userId);
}
