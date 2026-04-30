package com.majordomo.adapter.in.event;

import com.majordomo.domain.model.AuditAction;
import com.majordomo.domain.model.AuditLogEntry;
import com.majordomo.domain.model.EntityType;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.event.JobPostingIngested;
import com.majordomo.domain.model.event.JobPostingScored;
import com.majordomo.domain.model.event.PostingDismissed;
import com.majordomo.domain.model.event.PostingMarkedApplied;
import com.majordomo.domain.model.event.PropertyArchived;
import com.majordomo.domain.model.event.ServiceRecordCreated;
import com.majordomo.domain.model.event.UserCreated;
import com.majordomo.domain.port.out.AuditLogRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Listens for domain events and persists audit log entries.
 */
@Component
public class AuditEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(AuditEventListener.class);

    private final AuditLogRepository auditLogRepository;

    /**
     * Constructs the listener with the audit log repository.
     *
     * @param auditLogRepository the outbound port for audit log persistence
     */
    public AuditEventListener(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Records an audit entry when a service record is created.
     *
     * @param event the service record creation event
     */
    @EventListener
    public void onServiceRecordCreated(ServiceRecordCreated event) {
        log(event.organizationId(), EntityType.SERVICE_RECORD.name(), event.serviceRecordId(),
                AuditAction.CREATE.name(), event.occurredAt());
    }

    /**
     * Records an audit entry when a property is archived.
     *
     * @param event the property archived event
     */
    @EventListener
    public void onPropertyArchived(PropertyArchived event) {
        log(event.organizationId(), EntityType.PROPERTY.name(), event.propertyId(),
                AuditAction.ARCHIVE.name(), event.occurredAt());
    }

    /**
     * Records an audit entry when a user is created.
     *
     * @param event the user created event
     */
    @EventListener
    public void onUserCreated(UserCreated event) {
        log(event.organizationId(), EntityType.USER.name(), event.userId(),
                AuditAction.CREATE.name(), event.occurredAt());
    }

    /**
     * Records an audit entry when a job posting is ingested.
     *
     * @param event the posting ingestion event
     */
    @EventListener
    public void onJobPostingIngested(JobPostingIngested event) {
        log(event.organizationId(), EntityType.JOB_POSTING.name(), event.postingId(),
                AuditAction.CREATE.name(), event.occurredAt());
    }

    /**
     * Records an audit entry when a job posting is scored.
     *
     * @param event the scoring event
     */
    @EventListener
    public void onJobPostingScored(JobPostingScored event) {
        log(event.organizationId(), EntityType.SCORE_REPORT.name(), event.reportId(),
                AuditAction.CREATE.name(), event.occurredAt());
    }

    /**
     * Records an audit entry when a posting is marked as applied.
     *
     * @param event the apply event
     */
    @EventListener
    public void onPostingMarkedApplied(PostingMarkedApplied event) {
        log(event.organizationId(), EntityType.JOB_POSTING.name(), event.postingId(),
                AuditAction.APPLY.name(), event.occurredAt());
    }

    /**
     * Records an audit entry when a posting is dismissed.
     *
     * @param event the dismiss event
     */
    @EventListener
    public void onPostingDismissed(PostingDismissed event) {
        log(event.organizationId(), EntityType.JOB_POSTING.name(), event.postingId(),
                AuditAction.DISMISS.name(), event.occurredAt());
    }

    private void log(UUID organizationId, String entityType, UUID entityId,
                     String action, Instant occurredAt) {
        var entry = new AuditLogEntry();
        entry.setId(UuidFactory.newId());
        entry.setOrganizationId(organizationId);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setAction(action);
        entry.setOccurredAt(occurredAt);
        auditLogRepository.save(entry);
        LOG.debug("Audit log: {} {} {}", action, entityType, entityId);
    }
}
