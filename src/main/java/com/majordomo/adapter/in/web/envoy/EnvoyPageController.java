package com.majordomo.adapter.in.web.envoy;

import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.port.in.envoy.QueryScoreReportsUseCase;
import com.majordomo.domain.port.out.envoy.JobPostingRepository;
import com.majordomo.domain.port.out.identity.MembershipRepository;
import com.majordomo.domain.port.out.identity.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
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

    private static final int DEFAULT_LIMIT = 50;

    private final QueryScoreReportsUseCase reports;
    private final JobPostingRepository jobPostingRepository;
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;

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
     * Resolved authentication context: the user, plus the first org they
     * belong to. {@code organizationId} is {@code null} if the user has no
     * memberships, in which case the caller should redirect home.
     *
     * @param user           the authenticated user
     * @param organizationId the user's first organization id, or {@code null}
     */
    private record AuthContext(User user, UUID organizationId) { }

    /**
     * Constructs the controller.
     *
     * @param reports              inbound port for report queries
     * @param jobPostingRepository outbound port for posting lookups
     * @param userRepository       outbound port for user lookups
     * @param membershipRepository outbound port for membership lookups
     */
    public EnvoyPageController(QueryScoreReportsUseCase reports,
                               JobPostingRepository jobPostingRepository,
                               UserRepository userRepository,
                               MembershipRepository membershipRepository) {
        this.reports = reports;
        this.jobPostingRepository = jobPostingRepository;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
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
        AuthContext ctx = resolveContext(principal);
        if (ctx.organizationId() == null) {
            return "redirect:/";
        }
        UUID orgId = ctx.organizationId();
        List<ScoreReport> recent = reports
                .query(orgId, minFinalScore, recommendation, null, DEFAULT_LIMIT)
                .items();

        List<ScoreReportRow> rows = recent.stream()
                .map(r -> new ScoreReportRow(
                        r,
                        jobPostingRepository.findById(r.postingId(), orgId).orElse(null)))
                .toList();

        model.addAttribute("rows", rows);
        model.addAttribute("minFinalScore", minFinalScore);
        model.addAttribute("recommendation", recommendation);
        model.addAttribute("organizationId", orgId);
        model.addAttribute("username", ctx.user().getUsername());
        return "envoy";
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
        AuthContext ctx = resolveContext(principal);
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

    /**
     * Resolves the authenticated user's first organization. Returns an
     * {@link AuthContext} whose {@code organizationId} is {@code null} when
     * the user has no membership; callers should redirect home in that case.
     */
    private AuthContext resolveContext(UserDetails principal) {
        var user = userRepository.findByUsername(principal.getUsername()).orElseThrow();
        var memberships = membershipRepository.findByUserId(user.getId());
        if (memberships.isEmpty()) {
            return new AuthContext(user, null);
        }
        return new AuthContext(user, memberships.get(0).getOrganizationId());
    }
}
