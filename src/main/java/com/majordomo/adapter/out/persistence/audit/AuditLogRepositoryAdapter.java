package com.majordomo.adapter.out.persistence.audit;

import com.majordomo.domain.model.AuditLogEntry;
import com.majordomo.domain.port.out.AuditLogRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Persistence adapter that fulfills the {@link AuditLogRepository}
 * output port by delegating to {@link JpaAuditLogRepository}.
 */
@Repository
public class AuditLogRepositoryAdapter implements AuditLogRepository {

    private final JpaAuditLogRepository jpa;

    /**
     * Constructs the adapter with the JPA repository.
     *
     * @param jpa the Spring Data JPA repository
     */
    public AuditLogRepositoryAdapter(JpaAuditLogRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public AuditLogEntry save(AuditLogEntry entry) {
        var entity = AuditLogMapper.toEntity(entry);
        return AuditLogMapper.toDomain(jpa.save(entity));
    }

    @Override
    public List<AuditLogEntry> findByEntityTypeAndEntityId(String entityType, UUID entityId) {
        return jpa.findByEntityTypeAndEntityIdOrderByOccurredAtDesc(entityType, entityId)
                .stream()
                .map(AuditLogMapper::toDomain)
                .toList();
    }

    @Override
    public List<AuditLogEntry> findByUserId(UUID userId) {
        return jpa.findByUserIdOrderByOccurredAtDesc(userId)
                .stream()
                .map(AuditLogMapper::toDomain)
                .toList();
    }

    @Override
    public List<AuditLogEntry> find(UUID organizationId, String entityType, UUID userId,
                                    Instant since, Instant until, int limit) {
        return jpa.findScoped(organizationId, entityType, userId, since, until,
                        PageRequest.of(0, Math.max(1, limit)))
                .stream()
                .map(AuditLogMapper::toDomain)
                .toList();
    }
}
