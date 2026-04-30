package com.majordomo.adapter.in.event;

import com.majordomo.domain.model.AuditLogEntry;
import com.majordomo.domain.model.event.PostingDismissed;
import com.majordomo.domain.model.event.PostingMarkedApplied;
import com.majordomo.domain.model.event.PropertyArchived;
import com.majordomo.domain.model.event.ServiceRecordCreated;
import com.majordomo.domain.model.event.UserCreated;
import com.majordomo.domain.port.out.AuditLogRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AuditEventListener}'s registry-driven dispatch.
 */
class AuditEventListenerTest {

    private AuditLogRepository auditLogRepository;
    private AuditExtractorRegistry registry;
    private AuditEventListener listener;

    @BeforeEach
    void setUp() {
        auditLogRepository = mock(AuditLogRepository.class);
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        registry = new AuditExtractorRegistry();
        listener = new AuditEventListener(auditLogRepository, registry);
        listener.registerExtractors();
    }

    /** ServiceRecordCreated → CREATE audit entry for SERVICE_RECORD. */
    @Test
    void serviceRecordCreatedWritesAuditEntry() {
        UUID recordId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        Instant now = Instant.now();

        listener.onEvent(new ServiceRecordCreated(recordId, orgId, propertyId, null, now));

        AuditLogEntry entry = capture();
        assertThat(entry.getId()).isNotNull();
        assertThat(entry.getOrganizationId()).isEqualTo(orgId);
        assertThat(entry.getEntityType()).isEqualTo("SERVICE_RECORD");
        assertThat(entry.getEntityId()).isEqualTo(recordId);
        assertThat(entry.getAction()).isEqualTo("CREATE");
        assertThat(entry.getOccurredAt()).isEqualTo(now);
    }

    /** PropertyArchived → ARCHIVE audit entry for PROPERTY. */
    @Test
    void propertyArchivedWritesAuditEntry() {
        UUID propertyId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Instant now = Instant.now();

        listener.onEvent(new PropertyArchived(propertyId, orgId, now));

        AuditLogEntry entry = capture();
        assertThat(entry.getEntityType()).isEqualTo("PROPERTY");
        assertThat(entry.getEntityId()).isEqualTo(propertyId);
        assertThat(entry.getAction()).isEqualTo("ARCHIVE");
        assertThat(entry.getOccurredAt()).isEqualTo(now);
    }

    /** PostingMarkedApplied → APPLY audit entry for JOB_POSTING. */
    @Test
    void postingMarkedAppliedWritesAuditEntry() {
        UUID postingId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Instant now = Instant.now();

        listener.onEvent(new PostingMarkedApplied(postingId, orgId, now));

        AuditLogEntry entry = capture();
        assertThat(entry.getEntityType()).isEqualTo("JOB_POSTING");
        assertThat(entry.getAction()).isEqualTo("APPLY");
        assertThat(entry.getEntityId()).isEqualTo(postingId);
    }

    /** PostingDismissed → DISMISS audit entry for JOB_POSTING. */
    @Test
    void postingDismissedWritesAuditEntry() {
        UUID postingId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Instant now = Instant.now();

        listener.onEvent(new PostingDismissed(postingId, orgId, now));

        AuditLogEntry entry = capture();
        assertThat(entry.getEntityType()).isEqualTo("JOB_POSTING");
        assertThat(entry.getAction()).isEqualTo("DISMISS");
    }

    /** UserCreated → CREATE audit entry for USER. */
    @Test
    void userCreatedWritesAuditEntry() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Instant now = Instant.now();

        listener.onEvent(new UserCreated(userId, orgId, "testuser", now));

        AuditLogEntry entry = capture();
        assertThat(entry.getEntityType()).isEqualTo("USER");
        assertThat(entry.getEntityId()).isEqualTo(userId);
        assertThat(entry.getAction()).isEqualTo("CREATE");
    }

    /** Events without a registered extractor are ignored, not failed. */
    @Test
    void unregisteredEventIsIgnored() {
        listener.onEvent("an-unrelated-event-payload");
        verify(auditLogRepository, never()).save(any());
    }

    private AuditLogEntry capture() {
        var captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditLogRepository).save(captor.capture());
        return captor.getValue();
    }
}
