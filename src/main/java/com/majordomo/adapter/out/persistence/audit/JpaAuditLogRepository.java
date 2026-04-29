package com.majordomo.adapter.out.persistence.audit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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

    /**
     * Queries entries scoped to an organization with optional filters; null
     * parameters skip that filter. Results are ordered by occurred_at desc.
     *
     * @param organizationId required organization scope
     * @param entityType     optional entity-type filter
     * @param userId         optional actor filter
     * @param since          optional inclusive lower bound on occurred_at
     * @param until          optional exclusive upper bound on occurred_at
     * @param pageable       pagination/limit
     * @return matching entries
     */
    @Query("SELECT a FROM AuditLogEntity a WHERE a.organizationId = :orgId"
            + " AND (:entityType IS NULL OR a.entityType = :entityType)"
            + " AND (:userId IS NULL OR a.userId = :userId)"
            + " AND (:since IS NULL OR a.occurredAt >= :since)"
            + " AND (:until IS NULL OR a.occurredAt < :until)"
            + " ORDER BY a.occurredAt DESC")
    List<AuditLogEntity> findScoped(@Param("orgId") UUID organizationId,
                                    @Param("entityType") String entityType,
                                    @Param("userId") UUID userId,
                                    @Param("since") Instant since,
                                    @Param("until") Instant until,
                                    Pageable pageable);
}
