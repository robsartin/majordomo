package com.majordomo.application.envoy;

import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.event.PostingDismissed;
import com.majordomo.domain.model.event.PostingMarkedApplied;
import com.majordomo.domain.port.in.envoy.MarkPostingConversionUseCase;
import com.majordomo.domain.port.out.EventPublisher;
import com.majordomo.domain.port.out.envoy.JobPostingRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Records the user's response to an APPLY_NOW recommendation: either applied
 * or dismissed. Both are terminal — re-marking has no effect. Each transition
 * publishes a domain event (consumed by the audit listener) and increments
 * a Prometheus counter so conversion rate is observable.
 */
@Service
public class PostingConversionService implements MarkPostingConversionUseCase {

    /** Total APPLY_NOW conversions, tagged by org and outcome={applied,dismissed}. */
    static final String CONVERSION_METRIC = "envoy_apply_now_conversion_total";

    private final JobPostingRepository postings;
    private final EventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    /**
     * Constructs the service.
     *
     * @param postings       outbound port for postings
     * @param eventPublisher domain event publisher
     * @param meterRegistry  metrics registry for the conversion counter
     */
    public PostingConversionService(JobPostingRepository postings,
                                    EventPublisher eventPublisher,
                                    MeterRegistry meterRegistry) {
        this.postings = postings;
        this.eventPublisher = eventPublisher;
        this.meterRegistry = meterRegistry;
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
        Counter.builder(CONVERSION_METRIC)
                .description("APPLY_NOW posting conversions, by outcome")
                .tag("org", organizationId.toString())
                .tag("outcome", "applied")
                .register(meterRegistry)
                .increment();
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
        Counter.builder(CONVERSION_METRIC)
                .description("APPLY_NOW posting conversions, by outcome")
                .tag("org", organizationId.toString())
                .tag("outcome", "dismissed")
                .register(meterRegistry)
                .increment();
    }
}
