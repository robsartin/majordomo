package com.majordomo.adapter.in.web.envoy;

import com.majordomo.adapter.in.web.envoy.dto.IngestPostingRequest;
import com.majordomo.application.identity.OrganizationAccessService;
import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.JobSourceRequest;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.port.in.envoy.IngestJobPostingUseCase;
import com.majordomo.domain.port.in.envoy.ScoreJobPostingUseCase;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

/** REST controller for ingesting and scoring job postings. */
@RestController
@RequestMapping("/api/envoy/postings")
@Tag(name = "Envoy", description = "Job posting ingestion and scoring")
public class PostingController {

    private final IngestJobPostingUseCase ingestUseCase;
    private final ScoreJobPostingUseCase scoreUseCase;
    private final OrganizationAccessService organizationAccessService;

    /**
     * Constructs the controller.
     *
     * @param ingestUseCase             inbound port for ingestion
     * @param scoreUseCase              inbound port for scoring
     * @param organizationAccessService enforces per-org access control
     */
    public PostingController(IngestJobPostingUseCase ingestUseCase,
                             ScoreJobPostingUseCase scoreUseCase,
                             OrganizationAccessService organizationAccessService) {
        this.ingestUseCase = ingestUseCase;
        this.scoreUseCase = scoreUseCase;
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
}
