package com.majordomo.adapter.in.web.envoy;

import com.majordomo.application.envoy.LlmScoringException;
import com.majordomo.application.identity.CurrentOrganizationResolver;
import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.JobSourceRequest;
import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.port.in.envoy.GetApplyNowConversionStatUseCase;
import com.majordomo.domain.port.in.envoy.IngestJobPostingUseCase;
import com.majordomo.domain.port.in.envoy.MarkPostingConversionUseCase;
import com.majordomo.domain.port.in.envoy.QueryScoreReportsUseCase;
import com.majordomo.domain.port.in.envoy.ScoreJobPostingUseCase;
import com.majordomo.domain.port.out.envoy.JobPostingRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Thymeleaf controller for the Envoy tab. Renders a paginated list of recent
 * score reports for the authenticated user's first organization, each enriched
 * with its source {@link JobPosting}'s company / title / location, plus a
 * per-report detail page at {@code /envoy/reports/{id}}.
 */
@Controller
public class EnvoyPageController {

    private static final Logger LOG = LoggerFactory.getLogger(EnvoyPageController.class);

    private static final int DEFAULT_LIMIT = 50;

    /** Rubric used by the inline ingest form. v1 hardcodes "default"; rubric picker is out of scope. */
    private static final String DEFAULT_RUBRIC = "default";

    private final QueryScoreReportsUseCase reports;
    private final IngestJobPostingUseCase ingestUseCase;
    private final ScoreJobPostingUseCase scoreUseCase;
    private final MarkPostingConversionUseCase conversionUseCase;
    private final GetApplyNowConversionStatUseCase conversionStatUseCase;
    private final JobPostingRepository jobPostingRepository;
    private final CurrentOrganizationResolver currentOrg;

    /**
     * View-model row binding a {@link ScoreReport} to its source {@link JobPosting}.
     * The posting is nullable to defensively handle the case where a report
     * references a posting that has been deleted from underneath it.
     *
     * @param report  the score report
     * @param posting the source posting, or {@code null} if no longer present
     */
    public record ScoreReportRow(ScoreReport report, JobPosting posting) { }

    /**
     * Constructs the controller.
     *
     * @param reports               inbound port for report queries
     * @param ingestUseCase         inbound port for ingesting a posting from any source
     * @param scoreUseCase          inbound port for scoring an ingested posting
     * @param conversionUseCase     inbound port for marking APPLY_NOW conversion outcome
     * @param conversionStatUseCase inbound port for the APPLY_NOW conversion rollup
     * @param jobPostingRepository  outbound port for posting lookups
     * @param currentOrg            resolves the authenticated user's first organization
     */
    public EnvoyPageController(QueryScoreReportsUseCase reports,
                               IngestJobPostingUseCase ingestUseCase,
                               ScoreJobPostingUseCase scoreUseCase,
                               MarkPostingConversionUseCase conversionUseCase,
                               GetApplyNowConversionStatUseCase conversionStatUseCase,
                               JobPostingRepository jobPostingRepository,
                               CurrentOrganizationResolver currentOrg) {
        this.reports = reports;
        this.ingestUseCase = ingestUseCase;
        this.scoreUseCase = scoreUseCase;
        this.conversionUseCase = conversionUseCase;
        this.conversionStatUseCase = conversionStatUseCase;
        this.jobPostingRepository = jobPostingRepository;
        this.currentOrg = currentOrg;
    }

    /**
     * Renders the Envoy report list for the authenticated user's first
     * organization. Redirects home if the user has no membership.
     *
     * <p>Each report is paired with its source posting via a per-row
     * {@link JobPostingRepository#findById(UUID, UUID)} lookup. This is N+1 by
     * design: at the {@code DEFAULT_LIMIT} of 50 the cost is negligible, and
     * the read path stays simple. If the page grows we can swap in a join
     * query without touching the template.</p>
     *
     * <p>Optional filters {@code minFinalScore} and {@code recommendation} are
     * threaded straight through to the use case. The raw values are also
     * exposed on the model so the filter strip can pre-populate after a
     * submit.</p>
     *
     * @param minFinalScore  optional min score lower bound (null = no filter)
     * @param recommendation optional recommendation tier filter (null = any)
     * @param principal      the authenticated user
     * @param model          the Thymeleaf model
     * @return the {@code envoy} template name, or a redirect to {@code /} if
     *         the user has no organization
     */
    @GetMapping("/envoy")
    public String envoy(@RequestParam(required = false) Integer minFinalScore,
                        @RequestParam(required = false) Recommendation recommendation,
                        @AuthenticationPrincipal UserDetails principal,
                        Model model) {
        var ctx = currentOrg.resolve(principal);
        if (ctx.organizationId() == null) {
            return "redirect:/";
        }
        renderEnvoyPage(ctx, minFinalScore, recommendation, model);
        return "envoy";
    }

    /**
     * Handles the inline "Score a posting" form on {@code /envoy}. Ingests the
     * pasted payload via the appropriate {@code JobSource}, scores the resulting
     * posting against the {@code default} rubric, then redirects to {@code GET
     * /envoy} so the new row renders at the top of the list.
     *
     * <p>If ingest fails ({@link IllegalArgumentException} — typically "no
     * JobSource supports type: …") or scoring fails ({@link
     * LlmScoringException}), the page is re-rendered in place with an
     * {@code ingestError} model attribute so the message survives without a
     * separate flash redirect. This keeps the failure path stateless and
     * trivially testable.</p>
     *
     * <p>Optional hint fields ({@code company}, {@code title}, {@code location})
     * are forwarded to the use case as a {@code Map} with blank values
     * filtered out, so the LLM extractor only sees hints the caller actually
     * provided.</p>
     *
     * @param type      source discriminator (defaults to {@code "manual"})
     * @param payload   raw posting text / URL / source-specific id
     * @param company   optional company hint
     * @param title     optional title hint
     * @param location  optional location hint
     * @param principal the authenticated user
     * @param model     the Thymeleaf model
     * @return {@code redirect:/envoy} on success; {@code envoy} (with an
     *         {@code ingestError} attribute) on a handled failure;
     *         {@code redirect:/} if the user has no organization
     */
    @PostMapping("/envoy")
    public String submitIngest(@RequestParam(defaultValue = "manual") String type,
                               @RequestParam(required = false) String payload,
                               @RequestParam(required = false) String company,
                               @RequestParam(required = false) String title,
                               @RequestParam(required = false) String location,
                               @AuthenticationPrincipal UserDetails principal,
                               Model model) {
        var ctx = currentOrg.resolve(principal);
        if (ctx.organizationId() == null) {
            return "redirect:/";
        }
        UUID orgId = ctx.organizationId();

        if (payload == null || payload.isBlank()) {
            renderEnvoyPage(ctx, null, null, model);
            model.addAttribute("ingestError", "Payload is required.");
            return "envoy";
        }

        Map<String, String> hints = buildHints(company, title, location);
        JobSourceRequest request = new JobSourceRequest(type, payload, hints);

        try {
            JobPosting saved = ingestUseCase.ingest(request, orgId);
            scoreUseCase.score(saved.getId(), DEFAULT_RUBRIC, orgId);
            return "redirect:/envoy";
        } catch (IllegalArgumentException | LlmScoringException ex) {
            LOG.warn("Inline ingest+score failed for org {} (type={}): {}",
                    orgId, type, ex.getMessage());
            renderEnvoyPage(ctx, null, null, model);
            model.addAttribute("ingestError", ex.getMessage());
            return "envoy";
        }
    }

    /**
     * Populates the model with the data needed to render the envoy list page
     * (rows, filter echo values, org id, username). Shared between the GET
     * handler and the POST handler's error path.
     */
    private void renderEnvoyPage(CurrentOrganizationResolver.Resolved ctx,
                                 Integer minFinalScore,
                                 Recommendation recommendation,
                                 Model model) {
        UUID orgId = ctx.organizationId();
        List<ScoreReport> recent = reports
                .query(orgId, minFinalScore, recommendation, null, DEFAULT_LIMIT)
                .items();

        List<ScoreReportRow> rows = recent.stream()
                .map(r -> new ScoreReportRow(
                        r,
                        jobPostingRepository.findById(r.postingId(), orgId).orElse(null)))
                .toList();

        var stat = conversionStatUseCase.getStat(orgId);

        model.addAttribute("rows", rows);
        model.addAttribute("minFinalScore", minFinalScore);
        model.addAttribute("recommendation", recommendation);
        model.addAttribute("organizationId", orgId);
        model.addAttribute("username", ctx.user().getUsername());
        model.addAttribute("applyNowTotal", stat.total());
        model.addAttribute("applyNowApplied", stat.applied());
    }

    /**
     * Builds the hint map for the ingest request, dropping blank entries so the
     * downstream LLM extractor doesn't see noise.
     */
    private static Map<String, String> buildHints(String company,
                                                  String title,
                                                  String location) {
        Map<String, String> hints = new LinkedHashMap<>();
        putIfNotBlank(hints, "company", company);
        putIfNotBlank(hints, "title", title);
        putIfNotBlank(hints, "location", location);
        return hints;
    }

    private static void putIfNotBlank(Map<String, String> hints, String key, String value) {
        if (value != null && !value.isBlank()) {
            hints.put(key, value.trim());
        }
    }

    /**
     * Marks a posting as applied (APPLY_NOW conversion path). Idempotent.
     *
     * @param postingId the posting id (path variable)
     * @param principal the authenticated user
     * @return redirect back to the report detail page if the posting has any
     *         report; otherwise to the envoy list
     */
    @PostMapping("/envoy/postings/{postingId}/applied")
    public String markPostingApplied(@PathVariable UUID postingId,
                                     @AuthenticationPrincipal UserDetails principal) {
        var ctx = currentOrg.resolve(principal);
        if (ctx.organizationId() == null) {
            return "redirect:/";
        }
        try {
            conversionUseCase.markApplied(postingId, ctx.organizationId());
        } catch (IllegalArgumentException ignored) {
            // Posting not in user's org — silently swallow; the redirect target
            // will naturally 404 if the report id is not theirs.
        }
        return "redirect:/envoy";
    }

    /**
     * Marks a posting as dismissed (not interested). Idempotent.
     *
     * @param postingId the posting id (path variable)
     * @param principal the authenticated user
     * @return redirect to the envoy list
     */
    @PostMapping("/envoy/postings/{postingId}/dismissed")
    public String dismissPosting(@PathVariable UUID postingId,
                                 @AuthenticationPrincipal UserDetails principal) {
        var ctx = currentOrg.resolve(principal);
        if (ctx.organizationId() == null) {
            return "redirect:/";
        }
        try {
            conversionUseCase.dismiss(postingId, ctx.organizationId());
        } catch (IllegalArgumentException ignored) {
            // Same rationale as markPostingApplied.
        }
        return "redirect:/envoy";
    }

    /**
     * Renders the human-readable detail page for a single score report. The
     * detail page surfaces every category rationale and flag rationale the LLM
     * produced, so the user can see <em>why</em> a posting received its
     * recommendation.
     *
     * <p>If the report is not in the user's first organization (or simply
     * doesn't exist) this returns 404. If the report's source posting has been
     * deleted from underneath the report we still render — the {@code posting}
     * model attribute is simply absent and the template degrades gracefully.</p>
     *
     * @param id        the report id
     * @param principal the authenticated user
     * @param model     the Thymeleaf model
     * @param response  used to set the 404 status when no report is found
     * @return the {@code envoy-report} template, {@code error} on 404, or a
     *         redirect to {@code /} if the user has no organization
     */
    @GetMapping("/envoy/reports/{id}")
    public String getReport(@PathVariable UUID id,
                            @AuthenticationPrincipal UserDetails principal,
                            Model model,
                            HttpServletResponse response) {
        var ctx = currentOrg.resolve(principal);
        if (ctx.organizationId() == null) {
            return "redirect:/";
        }
        UUID orgId = ctx.organizationId();

        Optional<ScoreReport> reportOpt = reports.findById(id, orgId);
        if (reportOpt.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("message", "Score report not found.");
            return "error";
        }

        ScoreReport report = reportOpt.get();
        jobPostingRepository.findById(report.postingId(), orgId)
                .ifPresent(p -> model.addAttribute("posting", p));

        model.addAttribute("report", report);
        model.addAttribute("organizationId", orgId);
        model.addAttribute("username", ctx.user().getUsername());
        return "envoy-report";
    }

}
