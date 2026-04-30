package com.majordomo.adapter.in.event;

import com.majordomo.domain.model.AuditAction;
import com.majordomo.domain.model.AuditLogEntry;
import com.majordomo.domain.model.EntityType;
import com.majordomo.domain.model.event.JobPostingIngested;
import com.majordomo.domain.model.event.JobPostingScored;
import com.majordomo.domain.model.event.PostingDismissed;
import com.majordomo.domain.model.event.PostingMarkedApplied;
import com.majordomo.domain.model.event.PropertyArchived;
import com.majordomo.domain.model.event.ServiceRecordCreated;
import com.majordomo.domain.model.event.UserCreated;
import com.majordomo.domain.port.out.AuditLogRepository;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Persists audit log entries for state-changing domain events. Adding a new
 * audited event type is a single registration in {@link #registerExtractors()}
 * — no new {@code @EventListener} method, no new test seam.
 */
@Component
public class AuditEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(AuditEventListener.class);

    private final AuditLogRepository auditLogRepository;
    private final AuditExtractorRegistry registry;

    /**
     * Constructs the listener.
     *
     * @param auditLogRepository outbound port for audit log persistence
     * @param registry           registry of per-event extractors
     */
    public AuditEventListener(AuditLogRepository auditLogRepository,
                              AuditExtractorRegistry registry) {
        this.auditLogRepository = auditLogRepository;
        this.registry = registry;
    }

    /**
     * Registers every extractor at bean construction. New audited events:
     * add one line here.
     */
    @PostConstruct
    public void registerExtractors() {
        registry.register(ServiceRecordCreated.class, e -> new AuditExtraction(
                e.organizationId(), EntityType.SERVICE_RECORD.name(), e.serviceRecordId(),
                AuditAction.CREATE.name(), e.occurredAt()));
        registry.register(PropertyArchived.class, e -> new AuditExtraction(
                e.organizationId(), EntityType.PROPERTY.name(), e.propertyId(),
                AuditAction.ARCHIVE.name(), e.occurredAt()));
        registry.register(UserCreated.class, e -> new AuditExtraction(
                e.organizationId(), EntityType.USER.name(), e.userId(),
                AuditAction.CREATE.name(), e.occurredAt()));
        registry.register(JobPostingIngested.class, e -> new AuditExtraction(
                e.organizationId(), EntityType.JOB_POSTING.name(), e.postingId(),
                AuditAction.CREATE.name(), e.occurredAt()));
        registry.register(JobPostingScored.class, e -> new AuditExtraction(
                e.organizationId(), EntityType.SCORE_REPORT.name(), e.reportId(),
                AuditAction.CREATE.name(), e.occurredAt()));
        registry.register(PostingMarkedApplied.class, e -> new AuditExtraction(
                e.organizationId(), EntityType.JOB_POSTING.name(), e.postingId(),
                AuditAction.APPLY.name(), e.occurredAt()));
        registry.register(PostingDismissed.class, e -> new AuditExtraction(
                e.organizationId(), EntityType.JOB_POSTING.name(), e.postingId(),
                AuditAction.DISMISS.name(), e.occurredAt()));
    }

    /**
     * Single generic listener: find the extractor for the event's class and
     * persist whatever it produces. Events without a registered extractor are
     * ignored (so the listener doesn't fail on unrelated app events).
     *
     * @param event any application event
     */
    @EventListener
    public void onEvent(Object event) {
        registry.extract(event).ifPresent(this::persist);
    }

    private void persist(AuditLogEntry entry) {
        auditLogRepository.save(entry);
        LOG.debug("Audit log: {} {} {}",
                entry.getAction(), entry.getEntityType(), entry.getEntityId());
    }
}
