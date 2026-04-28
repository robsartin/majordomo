package com.majordomo.application.envoy;

import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.event.PostingDismissed;
import com.majordomo.domain.model.event.PostingMarkedApplied;
import com.majordomo.domain.port.out.EventPublisher;
import com.majordomo.domain.port.out.envoy.JobPostingRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link PostingConversionService}.
 */
class PostingConversionServiceTest {

    private final JobPostingRepository postings = mock(JobPostingRepository.class);
    private final EventPublisher events = mock(EventPublisher.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final PostingConversionService service =
            new PostingConversionService(postings, events, meterRegistry);

    /** markApplied stamps appliedAt, persists, publishes the event, and increments the applied counter. */
    @Test
    void markAppliedHappyPath() {
        UUID orgId = UUID.randomUUID();
        UUID postingId = UUID.randomUUID();
        JobPosting posting = posting(postingId, orgId);
        when(postings.findById(postingId, orgId)).thenReturn(Optional.of(posting));
        Instant before = Instant.now();

        service.markApplied(postingId, orgId);

        assertThat(posting.getAppliedAt()).isBetween(before, Instant.now());
        verify(postings).save(posting);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(events).publish(captor.capture());
        PostingMarkedApplied event = (PostingMarkedApplied) captor.getValue();
        assertThat(event.postingId()).isEqualTo(postingId);
        assertThat(event.organizationId()).isEqualTo(orgId);
        assertThat(event.occurredAt()).isEqualTo(posting.getAppliedAt());
        assertThat(counterCount("applied", orgId)).isEqualTo(1);
    }

    /** dismiss stamps dismissedAt, persists, publishes the event, and increments the dismissed counter. */
    @Test
    void dismissHappyPath() {
        UUID orgId = UUID.randomUUID();
        UUID postingId = UUID.randomUUID();
        JobPosting posting = posting(postingId, orgId);
        when(postings.findById(postingId, orgId)).thenReturn(Optional.of(posting));
        Instant before = Instant.now();

        service.dismiss(postingId, orgId);

        assertThat(posting.getDismissedAt()).isBetween(before, Instant.now());
        verify(postings).save(posting);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(events).publish(captor.capture());
        PostingDismissed event = (PostingDismissed) captor.getValue();
        assertThat(event.postingId()).isEqualTo(postingId);
        assertThat(event.organizationId()).isEqualTo(orgId);
        assertThat(counterCount("dismissed", orgId)).isEqualTo(1);
    }

    /** Re-marking an already-applied posting is a no-op (no save, no event, no counter increment). */
    @Test
    void markAppliedIsIdempotent() {
        UUID orgId = UUID.randomUUID();
        UUID postingId = UUID.randomUUID();
        JobPosting posting = posting(postingId, orgId);
        posting.setAppliedAt(Instant.parse("2025-01-01T00:00:00Z"));
        when(postings.findById(postingId, orgId)).thenReturn(Optional.of(posting));

        service.markApplied(postingId, orgId);

        assertThat(posting.getAppliedAt()).isEqualTo(Instant.parse("2025-01-01T00:00:00Z"));
        verify(postings, never()).save(any());
        verify(events, never()).publish(any());
        assertThat(counterCount("applied", orgId)).isEqualTo(0);
    }

    /** Re-dismissing is a no-op. */
    @Test
    void dismissIsIdempotent() {
        UUID orgId = UUID.randomUUID();
        UUID postingId = UUID.randomUUID();
        JobPosting posting = posting(postingId, orgId);
        posting.setDismissedAt(Instant.parse("2025-01-01T00:00:00Z"));
        when(postings.findById(postingId, orgId)).thenReturn(Optional.of(posting));

        service.dismiss(postingId, orgId);

        verify(postings, never()).save(any());
        verify(events, never()).publish(any());
    }

    /** Missing posting (or wrong org) results in IllegalArgumentException. */
    @Test
    void markAppliedThrowsWhenPostingMissing() {
        UUID orgId = UUID.randomUUID();
        UUID postingId = UUID.randomUUID();
        when(postings.findById(postingId, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markApplied(postingId, orgId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(postingId.toString());
        verify(postings, never()).save(any());
        verify(events, never()).publish(any());
    }

    /** Both transitions can fire on the same posting (apply, then dismiss) — independent counters. */
    @Test
    void appliedAndDismissedAreIndependent() {
        UUID orgId = UUID.randomUUID();
        UUID postingId = UUID.randomUUID();
        JobPosting posting = posting(postingId, orgId);
        when(postings.findById(postingId, orgId)).thenReturn(Optional.of(posting));

        service.markApplied(postingId, orgId);
        service.dismiss(postingId, orgId);

        assertThat(posting.getAppliedAt()).isNotNull();
        assertThat(posting.getDismissedAt()).isNotNull();
        verify(postings, times(2)).save(posting);
        assertThat(counterCount("applied", orgId)).isEqualTo(1);
        assertThat(counterCount("dismissed", orgId)).isEqualTo(1);
    }

    private static JobPosting posting(UUID id, UUID orgId) {
        JobPosting p = new JobPosting();
        p.setId(id);
        p.setOrganizationId(orgId);
        p.setSource("manual");
        p.setRawText("body");
        return p;
    }

    private double counterCount(String outcome, UUID orgId) {
        var counter = meterRegistry.find(PostingConversionService.CONVERSION_METRIC)
                .tag("org", orgId.toString())
                .tag("outcome", outcome)
                .counter();
        return counter == null ? 0 : counter.count();
    }
}
