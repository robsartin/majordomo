package com.majordomo.adapter.in.web.envoy;

import com.majordomo.adapter.in.web.envoy.dto.IngestPostingRequest;
import com.majordomo.application.identity.OrganizationAccessService;
import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.JobSourceRequest;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.port.in.envoy.IngestJobPostingUseCase;
import com.majordomo.domain.port.in.envoy.ScoreJobPostingUseCase;
import com.majordomo.domain.port.out.envoy.JobPostingRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** REST controller for ingesting and scoring job postings. */
@RestController
@RequestMapping("/api/envoy/postings")
@Tag(name = "Envoy", description = "Job posting ingestion and scoring")
public class PostingController {

    private static final Logger LOG = LoggerFactory.getLogger(PostingController.class);

    private final IngestJobPostingUseCase ingestUseCase;
    private final ScoreJobPostingUseCase scoreUseCase;
    private final JobPostingRepository jobPostingRepository;
    private final OrganizationAccessService organizationAccessService;

    /**
     * Constructs the controller.
     *
     * @param ingestUseCase             inbound port for ingestion
     * @param scoreUseCase              inbound port for scoring
     * @param jobPostingRepository      outbound port for listing all postings in an org
     * @param organizationAccessService enforces per-org access control
     */
    public PostingController(IngestJobPostingUseCase ingestUseCase,
                             ScoreJobPostingUseCase scoreUseCase,
                             JobPostingRepository jobPostingRepository,
                             OrganizationAccessService organizationAccessService) {
        this.ingestUseCase = ingestUseCase;
        this.scoreUseCase = scoreUseCase;
        this.jobPostingRepository = jobPostingRepository;
        this.organizationAccessService = organizationAccessService;
    }

    /**
     * Ingests a posting from any supported source into the given org.
     *
     * @param organizationId owning org (caller must be a member)
     * @param req            ingestion request body
     * @return 201 Created with the persisted posting and Location header
     */
    @PostMapping
    public ResponseEntity<JobPosting> ingest(
            @RequestParam UUID organizationId,
            @Valid @RequestBody IngestPostingRequest req) {
        organizationAccessService.verifyAccess(organizationId);
        Map<String, String> hints = req.hints() == null ? Map.of() : req.hints();
        JobPosting saved = ingestUseCase.ingest(
                new JobSourceRequest(req.type(), req.payload(), hints), organizationId);
        return ResponseEntity
                .created(URI.create("/api/envoy/postings/" + saved.getId()))
                .body(saved);
    }

    /**
     * Scores an existing posting against the active rubric for the org.
     *
     * @param id             posting id
     * @param organizationId owning org (caller must be a member)
     * @param rubricName     rubric to score against (defaults to {@code "default"})
     * @return the persisted score report
     */
    @PostMapping("/{id}/score")
    public ResponseEntity<ScoreReport> score(
            @PathVariable UUID id,
            @RequestParam UUID organizationId,
            @RequestParam(defaultValue = "default") String rubricName) {
        organizationAccessService.verifyAccess(organizationId);
        return ResponseEntity.ok(scoreUseCase.score(id, rubricName, organizationId));
    }

    /**
     * Scores an existing posting against multiple rubrics. Each rubric produces
     * a separate persisted {@code ScoreReport} and a separate
     * {@code JobPostingScored} domain event. Fails fast: if any rubric cannot
     * be resolved the call returns 404 and no reports are persisted.
     *
     * @param id             posting id
     * @param organizationId owning org (caller must be a member)
     * @param rubricNames    rubric names to score against (repeat the param
     *                       or use a comma-separated value); must be non-empty
     * @return the persisted score reports, in the same order as {@code rubricNames}
     */
    @PostMapping("/{id}/score-all")
    public ResponseEntity<List<ScoreReport>> scoreAll(
            @PathVariable UUID id,
            @RequestParam UUID organizationId,
            @RequestParam List<String> rubricNames) {
        organizationAccessService.verifyAccess(organizationId);
        return ResponseEntity.ok(scoreUseCase.scoreAll(id, rubricNames, organizationId));
    }

    /**
     * Manually re-scores every posting in the org against the named rubric. The
     * automatic counterpart is the {@code RubricVersionCreated} listener fired
     * when a rubric is edited; this endpoint exposes the same fan-out for ad-hoc
     * use (e.g. after a model upgrade with no rubric change).
     *
     * <p>Throttling is delegated to the existing Resilience4j {@code envoy-llm}
     * policy on the LLM client — no new config. At personal scale this raw
     * fan-out is acceptable; queued/chunked backpressure can be added later if
     * the workload outgrows it.</p>
     *
     * @param organizationId owning org (caller must be a member)
     * @param rubricName     rubric to score against (defaults to {@code "default"})
     * @return JSON body with {@code count} = number of rescores triggered
     */
    @PostMapping("/rescore")
    public ResponseEntity<Map<String, Integer>> rescoreAll(
            @RequestParam UUID organizationId,
            @RequestParam(defaultValue = "default") String rubricName) {
        organizationAccessService.verifyAccess(organizationId);
        List<JobPosting> postings = jobPostingRepository.findAllByOrganizationId(organizationId);
        int succeeded = 0;
        for (JobPosting p : postings) {
            try {
                scoreUseCase.score(p.getId(), rubricName, organizationId);
                succeeded++;
            } catch (RuntimeException ex) {
                LOG.warn("Manual rescore failed for posting {} in org {}: {}",
                        p.getId(), organizationId, ex.getMessage());
            }
        }
        return ResponseEntity.ok(Map.of("count", succeeded));
    }
}
