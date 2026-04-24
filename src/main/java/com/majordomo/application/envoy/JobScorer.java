package com.majordomo.application.envoy;

import com.majordomo.domain.model.EntityNotFoundException;
import com.majordomo.domain.model.EntityType;
import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.LlmScoreResponse;
import com.majordomo.domain.model.envoy.Rubric;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.port.in.envoy.ScoreJobPostingUseCase;
import com.majordomo.domain.port.out.envoy.JobPostingRepository;
import com.majordomo.domain.port.out.envoy.LlmScoringPort;
import com.majordomo.domain.port.out.envoy.RubricRepository;
import com.majordomo.domain.port.out.envoy.ScoreReportRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Orchestrates posting scoring. Java owns all deterministic work: load the
 * posting and active rubric, render the prompt, validate the LLM response via
 * {@link ScoreAssembler}, and persist the report. The LLM makes only the fuzzy
 * interpretive choices.
 */
@Service
public class JobScorer implements ScoreJobPostingUseCase {

    private final RubricRepository rubrics;
    private final JobPostingRepository postings;
    private final ScoreReportRepository reports;
    private final LlmScoringPort llm;
    private final ScoreAssembler assembler;

    /**
     * Constructs the scorer with all required collaborators.
     *
     * @param rubrics   outbound port for rubrics
     * @param postings  outbound port for postings
     * @param reports   outbound port for score reports
     * @param llm       outbound LLM scoring port
     * @param assembler deterministic LLM-response validator
     */
    public JobScorer(RubricRepository rubrics,
                     JobPostingRepository postings,
                     ScoreReportRepository reports,
                     LlmScoringPort llm,
                     ScoreAssembler assembler) {
        this.rubrics = rubrics;
        this.postings = postings;
        this.reports = reports;
        this.llm = llm;
        this.assembler = assembler;
    }

    @Override
    public ScoreReport score(UUID postingId, String rubricName, UUID organizationId) {
        JobPosting posting = postings.findById(postingId, organizationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        EntityType.JOB_POSTING.name(), postingId));
        Rubric rubric = rubrics.findActiveByName(rubricName, organizationId)
                .orElseThrow(() -> new EntityNotFoundException("RUBRIC", rubricName));

        LlmScoreResponse resp = llm.score(posting, rubric);
        ScoreReport report = assembler.assemble(posting, rubric, resp, llm.modelId());
        return reports.save(report);
    }
}
