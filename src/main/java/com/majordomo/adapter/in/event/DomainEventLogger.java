package com.majordomo.adapter.in.event;

import com.majordomo.domain.model.event.PropertyArchived;
import com.majordomo.domain.model.event.ServiceRecordCreated;
import com.majordomo.domain.model.event.UserCreated;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Listens for domain events and logs them. Placeholder for future
 * cross-service coordination (cache invalidation, notifications, etc).
 */
@Component
public class DomainEventLogger {

    private static final Logger LOG = LoggerFactory.getLogger(DomainEventLogger.class);

    /**
     * Logs service record creation for future Ledger integration.
     *
     * @param event the service record creation event
     */
    @EventListener
    public void onServiceRecordCreated(ServiceRecordCreated event) {
        LOG.info("Service record created: {} for property {}",
                event.serviceRecordId(), event.propertyId());
    }

    /**
     * Logs property archival.
     *
     * @param event the property archived event
     */
    @EventListener
    public void onPropertyArchived(PropertyArchived event) {
        LOG.info("Property archived: {} in org {}",
                event.propertyId(), event.organizationId());
    }

    /**
     * Logs user creation.
     *
     * @param event the user created event
     */
    @EventListener
    public void onUserCreated(UserCreated event) {
        LOG.info("User created: {} ({}) in org {}",
                event.username(), event.userId(), event.organizationId());
    }
}
