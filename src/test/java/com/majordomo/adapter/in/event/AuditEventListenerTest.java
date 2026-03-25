package com.majordomo.adapter.in.event;

import com.majordomo.domain.model.AuditLogEntry;
import com.majordomo.domain.model.event.PropertyArchived;
import com.majordomo.domain.model.event.ServiceRecordCreated;
import com.majordomo.domain.model.event.UserCreated;
import com.majordomo.domain.port.out.AuditLogRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AuditEventListener}.
 */
class AuditEventListenerTest {

    private AuditLogRepository auditLogRepository;
    private AuditEventListener listener;

    @BeforeEach
    void setUp() {
        auditLogRepository = mock(AuditLogRepository.class);
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        listener = new AuditEventListener(auditLogRepository);
    }

    /** A ServiceRecordCreated event should produce a CREATE audit entry for ServiceRecord. */
    @Test
    void onServiceRecordCreatedWritesAuditEntry() {
        UUID recordId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        Instant now = Instant.now();

        listener.onServiceRecordCreated(
                new ServiceRecordCreated(recordId, propertyId, null, now));

        var captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLogEntry entry = captor.getValue();
        assertNotNull(entry.getId());
        assertEquals("ServiceRecord", entry.getEntityType());
        assertEquals(recordId, entry.getEntityId());
        assertEquals("CREATE", entry.getAction());
        assertEquals(now, entry.getOccurredAt());
    }

    /** A PropertyArchived event should produce an ARCHIVE audit entry for Property. */
    @Test
    void onPropertyArchivedWritesAuditEntry() {
        UUID propertyId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Instant now = Instant.now();

        listener.onPropertyArchived(new PropertyArchived(propertyId, orgId, now));

        var captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLogEntry entry = captor.getValue();
        assertNotNull(entry.getId());
        assertEquals("Property", entry.getEntityType());
        assertEquals(propertyId, entry.getEntityId());
        assertEquals("ARCHIVE", entry.getAction());
        assertEquals(now, entry.getOccurredAt());
    }

    /** A UserCreated event should produce a CREATE audit entry for User. */
    @Test
    void onUserCreatedWritesAuditEntry() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Instant now = Instant.now();

        listener.onUserCreated(new UserCreated(userId, orgId, "testuser", now));

        var captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLogEntry entry = captor.getValue();
        assertNotNull(entry.getId());
        assertEquals("User", entry.getEntityType());
        assertEquals(userId, entry.getEntityId());
        assertEquals("CREATE", entry.getAction());
        assertEquals(now, entry.getOccurredAt());
    }
}
