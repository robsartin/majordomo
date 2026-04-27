package com.majordomo.adapter.in.event;

import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.event.RubricVersionCreated;
import com.majordomo.domain.port.in.envoy.ScoreJobPostingUseCase;
import com.majordomo.domain.port.out.envoy.JobPostingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Listens for {@link RubricVersionCreated} and re-scores every posting in the
 * org against the new rubric version. The scoring path goes through
 * {@code ScoreJobPostingUseCase}, whose downstream {@code AnthropicMessageClient}
 * is already protected by the Resilience4j {@code envoy-llm} circuit breaker
 * and retry — so we get throttling and per-call resilience for free.
 *
 * <p>At personal scale (handful of postings) the raw fan-out here is acceptable
 * (issue #147). If a single rescore fails, we log and continue to the next
 * posting rather than aborting the whole batch.</p>
 */
@Component
public class RubricChangeRescoreListener {

    private static final Logger LOG = LoggerFactory.getLogger(RubricChangeRescoreListener.class);

    private final JobPostingRepository postings;
    private final ScoreJobPostingUseCase scoreUseCase;

    /**
     * Constructs the listener.
     *
     * @param postings     outbound port for fetching all postings in an org
     * @param scoreUseCase inbound scoring port (Resilience4j-protected at the LLM hop)
     */
    public RubricChangeRescoreListener(JobPostingRepository postings,
                                       ScoreJobPostingUseCase scoreUseCase) {
        this.postings = postings;
        this.scoreUseCase = scoreUseCase;
    }

    /**
     * Re-scores every posting in the org that owns the new rubric version.
     *
     * @param event the rubric-version-created event
     */
    @EventListener
    public void onRubricVersionCreated(RubricVersionCreated event) {
        List<JobPosting> all = postings.findAllByOrganizationId(event.organizationId());
        LOG.info("Rescoring {} postings against rubric '{}' v{} for org {}",
                all.size(), event.rubricName(), event.version(), event.organizationId());
        int succeeded = 0;
        int failed = 0;
        for (JobPosting p : all) {
            try {
                scoreUseCase.score(p.getId(), event.rubricName(), event.organizationId());
                succeeded++;
            } catch (RuntimeException ex) {
                failed++;
                LOG.warn("Rescore failed for posting {} in org {}: {}",
                        p.getId(), event.organizationId(), ex.getMessage());
            }
        }
        LOG.info("Rescore complete: {} succeeded, {} failed (rubric '{}' v{}, org {})",
                succeeded, failed, event.rubricName(), event.version(), event.organizationId());
    }
}
