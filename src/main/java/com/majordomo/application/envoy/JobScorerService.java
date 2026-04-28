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
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates posting scoring. Java owns all deterministic work: load the
 * posting and active rubric, render the prompt, validate the LLM response via
 * {@link ScoreAssembler}, and persist the report. The LLM makes only the fuzzy
 * interpretive choices.
 *
 * <p>LLM-call observability (token counters and latency timer) is delegated to
 * {@link EnvoyMetrics}.</p>
 */
@Service
public class JobScorerService implements ScoreJobPostingUseCase {

    private final RubricRepository rubrics;
    private final JobPostingRepository postings;
    private final ScoreReportRepository reports;
    private final LlmScoringPort llm;
    private final ScoreAssembler assembler;
    private final EventPublisher eventPublisher;
    private final EnvoyMetrics metrics;

    /**
     * Constructs the scorer with all required collaborators.
     *
     * @param rubrics        outbound port for rubrics
     * @param postings       outbound port for postings
     * @param reports        outbound port for score reports
     * @param llm            outbound LLM scoring port
     * @param assembler      deterministic LLM-response validator
     * @param eventPublisher domain event publisher
     * @param metrics        envoy metrics helper
     */
    public JobScorerService(RubricRepository rubrics,
                            JobPostingRepository postings,
                            ScoreReportRepository reports,
                            LlmScoringPort llm,
                            ScoreAssembler assembler,
                            EventPublisher eventPublisher,
                            EnvoyMetrics metrics) {
        this.rubrics = rubrics;
        this.postings = postings;
        this.reports = reports;
        this.llm = llm;
        this.assembler = assembler;
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
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
            metrics.recordLlmCallDuration(modelId, "error", System.nanoTime() - startNs);
            throw e;
        }
        metrics.recordLlmCallDuration(modelId, "success", System.nanoTime() - startNs);
        resp.usage().ifPresent(usage -> metrics.recordLlmTokenUsage(
                posting.getOrganizationId(), modelId, rubric.name(),
                usage.inputTokens(), usage.outputTokens()));

        ScoreReport report = assembler.assemble(posting, rubric, resp, modelId);
        ScoreReport saved = reports.save(report);
        eventPublisher.publish(new JobPostingScored(
                saved.id(), saved.organizationId(), saved.postingId(),
                saved.finalScore(), saved.recommendation(), Instant.now()));
        return saved;
    }
}
