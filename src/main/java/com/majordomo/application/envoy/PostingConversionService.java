package com.majordomo.application.envoy;

import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.event.PostingDismissed;
import com.majordomo.domain.model.event.PostingMarkedApplied;
import com.majordomo.domain.port.in.envoy.MarkPostingConversionUseCase;
import com.majordomo.domain.port.out.EventPublisher;
import com.majordomo.domain.port.out.envoy.JobPostingRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Records the user's response to an APPLY_NOW recommendation: either applied
 * or dismissed. Both are terminal — re-marking has no effect. Each transition
 * publishes a domain event (consumed by the audit listener) and delegates to
 * {@link EnvoyMetrics} for Prometheus instrumentation.
 */
@Service
public class PostingConversionService implements MarkPostingConversionUseCase {

    private final JobPostingRepository postings;
    private final EventPublisher eventPublisher;
    private final EnvoyMetrics metrics;

    /**
     * Constructs the service.
     *
     * @param postings       outbound port for postings
     * @param eventPublisher domain event publisher
     * @param metrics        envoy metrics helper
     */
    public PostingConversionService(JobPostingRepository postings,
                                    EventPublisher eventPublisher,
                                    EnvoyMetrics metrics) {
        this.postings = postings;
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
    }

    @Override
    public void markApplied(UUID postingId, UUID organizationId) {
        JobPosting posting = postings.findById(postingId, organizationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Posting not found: " + postingId));
        if (posting.getAppliedAt() != null) {
            return;
        }
        Instant now = Instant.now();
        posting.setAppliedAt(now);
        postings.save(posting);
        eventPublisher.publish(new PostingMarkedApplied(postingId, organizationId, now));
        metrics.recordApplyNowConversion(organizationId, EnvoyMetrics.ConversionOutcome.APPLIED);
    }

    @Override
    public void dismiss(UUID postingId, UUID organizationId) {
        JobPosting posting = postings.findById(postingId, organizationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Posting not found: " + postingId));
        if (posting.getDismissedAt() != null) {
            return;
        }
        Instant now = Instant.now();
        posting.setDismissedAt(now);
        postings.save(posting);
        eventPublisher.publish(new PostingDismissed(postingId, organizationId, now));
        metrics.recordApplyNowConversion(organizationId, EnvoyMetrics.ConversionOutcome.DISMISSED);
    }
}
