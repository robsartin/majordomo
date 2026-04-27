package com.majordomo.application.envoy;

import com.majordomo.domain.model.EntityNotFoundException;
import com.majordomo.domain.model.EntityType;
import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.LlmScoreResponse;
import com.majordomo.domain.model.envoy.Rubric;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.model.event.JobPostingScored;
import com.majordomo.domain.port.in.envoy.ScoreJobPostingUseCase;
import com.majordomo.domain.port.out.EventPublisher;
import com.majordomo.domain.port.out.envoy.JobPostingRepository;
import com.majordomo.domain.port.out.envoy.LlmScoringPort;
import com.majordomo.domain.port.out.envoy.RubricRepository;
import com.majordomo.domain.port.out.envoy.ScoreReportRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrates posting scoring. Java owns all deterministic work: load the
 * posting and active rubric, render the prompt, validate the LLM response via
 * {@link ScoreAssembler}, and persist the report. The LLM makes only the fuzzy
 * interpretive choices.
 *
 * <p>Each scoring call also records Prometheus metrics:
 * <ul>
 *     <li>{@code envoy_llm_input_tokens_total} (counter, tags: org, model, rubric)</li>
 *     <li>{@code envoy_llm_output_tokens_total} (counter, tags: org, model, rubric)</li>
 *     <li>{@code envoy_llm_call_duration} (timer, tags: model, outcome)</li>
 * </ul>
 * Token counters are only incremented when the LLM adapter supplies usage data.
 * The timer is always recorded so that latency is visible even for providers
 * that omit token counts.
 */
@Service
public class JobScorerService implements ScoreJobPostingUseCase {

    private static final String INPUT_TOKENS_METRIC = "envoy_llm_input_tokens_total";
    private static final String OUTPUT_TOKENS_METRIC = "envoy_llm_output_tokens_total";
    private static final String CALL_DURATION_METRIC = "envoy_llm_call_duration";

    private final RubricRepository rubrics;
    private final JobPostingRepository postings;
    private final ScoreReportRepository reports;
    private final LlmScoringPort llm;
    private final ScoreAssembler assembler;
    private final EventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    /**
     * Constructs the scorer with all required collaborators.
     *
     * @param rubrics        outbound port for rubrics
     * @param postings       outbound port for postings
     * @param reports        outbound port for score reports
     * @param llm            outbound LLM scoring port
     * @param assembler      deterministic LLM-response validator
     * @param eventPublisher domain event publisher
     * @param meterRegistry  Micrometer registry for token/latency metrics
     */
    public JobScorerService(RubricRepository rubrics,
                            JobPostingRepository postings,
                            ScoreReportRepository reports,
                            LlmScoringPort llm,
                            ScoreAssembler assembler,
                            EventPublisher eventPublisher,
                            MeterRegistry meterRegistry) {
        this.rubrics = rubrics;
        this.postings = postings;
        this.reports = reports;
        this.llm = llm;
        this.assembler = assembler;
        this.eventPublisher = eventPublisher;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public ScoreReport score(UUID postingId, String rubricName, UUID organizationId) {
        JobPosting posting = postings.findById(postingId, organizationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        EntityType.JOB_POSTING.name(), postingId));
        Rubric rubric = rubrics.findActiveByName(rubricName, organizationId)
                .orElseThrow(() -> new EntityNotFoundException("RUBRIC", rubricName));
        return runOne(posting, rubric);
    }

    @Override
    public List<ScoreReport> scoreAll(
            UUID postingId, List<String> rubricNames, UUID organizationId) {
        if (rubricNames == null || rubricNames.isEmpty()) {
            throw new IllegalArgumentException("rubricNames must be non-empty");
        }
        JobPosting posting = postings.findById(postingId, organizationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        EntityType.JOB_POSTING.name(), postingId));
        // Resolve all rubrics first so we fail fast without persisting partial state.
        List<Rubric> resolved = new ArrayList<>(rubricNames.size());
        for (String name : rubricNames) {
            resolved.add(rubrics.findActiveByName(name, organizationId)
                    .orElseThrow(() -> new EntityNotFoundException("RUBRIC", name)));
        }
        List<ScoreReport> saved = new ArrayList<>(resolved.size());
        for (Rubric rubric : resolved) {
            saved.add(runOne(posting, rubric));
        }
        return saved;
    }

    private ScoreReport runOne(JobPosting posting, Rubric rubric) {
        String modelId = llm.modelId();
        long startNs = System.nanoTime();
        LlmScoreResponse resp;
        try {
            resp = llm.score(posting, rubric);
        } catch (RuntimeException e) {
            recordTimer(modelId, "error", System.nanoTime() - startNs);
            throw e;
        }
        recordTimer(modelId, "success", System.nanoTime() - startNs);
        recordTokenUsage(resp, posting.getOrganizationId(), modelId, rubric.name());

        ScoreReport report = assembler.assemble(posting, rubric, resp, modelId);
        ScoreReport saved = reports.save(report);
        eventPublisher.publish(new JobPostingScored(
                saved.id(), saved.organizationId(), saved.postingId(),
                saved.finalScore(), saved.recommendation(), Instant.now()));
        return saved;
    }

    private void recordTimer(String modelId, String outcome, long elapsedNs) {
        Timer.builder(CALL_DURATION_METRIC)
                .description("Wall-clock duration of envoy LLM scoring calls")
                .tag("model", modelId)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(elapsedNs, TimeUnit.NANOSECONDS);
    }

    private void recordTokenUsage(LlmScoreResponse resp,
                                  UUID organizationId,
                                  String modelId,
                                  String rubricName) {
        resp.usage().ifPresent(usage -> {
            Counter.builder(INPUT_TOKENS_METRIC)
                    .description("Prompt tokens consumed by envoy LLM scoring calls")
                    .tag("org", organizationId.toString())
                    .tag("model", modelId)
                    .tag("rubric", rubricName)
                    .register(meterRegistry)
                    .increment(usage.inputTokens());
            Counter.builder(OUTPUT_TOKENS_METRIC)
                    .description("Completion tokens produced by envoy LLM scoring calls")
                    .tag("org", organizationId.toString())
                    .tag("model", modelId)
                    .tag("rubric", rubricName)
                    .register(meterRegistry)
                    .increment(usage.outputTokens());
        });
    }
}
