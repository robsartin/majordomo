package com.majordomo.adapter.in.event;

import com.majordomo.domain.model.AuditLogEntry;
import com.majordomo.domain.model.UuidFactory;
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
        log("ServiceRecord", event.serviceRecordId(), "CREATE", event.occurredAt());
    }

    /**
     * Records an audit entry when a property is archived.
     *
     * @param event the property archived event
     */
    @EventListener
    public void onPropertyArchived(PropertyArchived event) {
        log("Property", event.propertyId(), "ARCHIVE", event.occurredAt());
    }

    /**
     * Records an audit entry when a user is created.
     *
     * @param event the user created event
     */
    @EventListener
    public void onUserCreated(UserCreated event) {
        log("User", event.userId(), "CREATE", event.occurredAt());
    }

    private void log(String entityType, UUID entityId, String action, Instant occurredAt) {
        var entry = new AuditLogEntry();
        entry.setId(UuidFactory.newId());
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setAction(action);
        entry.setOccurredAt(occurredAt);
        auditLogRepository.save(entry);
        LOG.debug("Audit log: {} {} {}", action, entityType, entityId);
    }
}
