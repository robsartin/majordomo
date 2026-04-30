package com.majordomo.adapter.in.event;

import com.majordomo.domain.model.AuditAction;
import com.majordomo.domain.model.AuditLogEntry;
import com.majordomo.domain.model.EntityType;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.event.PropertyArchived;
import com.majordomo.domain.model.event.ServiceRecordCreated;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for {@link AuditExtractorRegistry} — no Spring context. */
class AuditExtractorRegistryTest {

    private AuditExtractorRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new AuditExtractorRegistry();
    }

    /** Cycle 1: registered extractor is found by event class and produces an AuditLogEntry. */
    @Test
    void registeredExtractorProducesAuditEntry() {
        registry.register(PropertyArchived.class, event ->
                new AuditExtraction(event.organizationId(), EntityType.PROPERTY.name(),
                        event.propertyId(), AuditAction.ARCHIVE.name(), event.occurredAt()));

        UUID orgId = UuidFactory.newId();
        UUID propertyId = UuidFactory.newId();
        Instant now = Instant.now();
        PropertyArchived event = new PropertyArchived(propertyId, orgId, now);

        Optional<AuditLogEntry> entry = registry.extract(event);

        assertThat(entry).isPresent();
        AuditLogEntry e = entry.get();
        assertThat(e.getOrganizationId()).isEqualTo(orgId);
        assertThat(e.getEntityType()).isEqualTo("PROPERTY");
        assertThat(e.getEntityId()).isEqualTo(propertyId);
        assertThat(e.getAction()).isEqualTo("ARCHIVE");
        assertThat(e.getOccurredAt()).isEqualTo(now);
        assertThat(e.getId()).isNotNull(); // registry mints an id
    }

    /** Cycle 2: events without a registered extractor return empty. */
    @Test
    void unregisteredEventReturnsEmpty() {
        UUID id = UuidFactory.newId();
        ServiceRecordCreated event = new ServiceRecordCreated(id, id, id, null, Instant.now());

        Optional<AuditLogEntry> entry = registry.extract(event);

        assertThat(entry).isEmpty();
    }
}
