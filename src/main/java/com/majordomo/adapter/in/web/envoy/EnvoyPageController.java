package com.majordomo.adapter.in.web.envoy;

import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.port.in.envoy.QueryScoreReportsUseCase;
import com.majordomo.domain.port.out.envoy.JobPostingRepository;
import com.majordomo.domain.port.out.identity.MembershipRepository;
import com.majordomo.domain.port.out.identity.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.UUID;

/**
 * Thymeleaf controller for the Envoy tab. Renders a paginated list of recent
 * score reports for the authenticated user's first organization, each enriched
 * with its source {@link JobPosting}'s company / title / location.
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
     * @param principal the authenticated user
     * @param model     the Thymeleaf model
     * @return the {@code envoy} template name, or a redirect to {@code /} if
     *         the user has no organization
     */
    @GetMapping("/envoy")
    public String envoy(@AuthenticationPrincipal UserDetails principal, Model model) {
        var user = userRepository.findByUsername(principal.getUsername()).orElseThrow();
        var memberships = membershipRepository.findByUserId(user.getId());
        if (memberships.isEmpty()) {
            return "redirect:/";
        }
        UUID orgId = memberships.get(0).getOrganizationId();
        List<ScoreReport> recent = reports
                .query(orgId, null, null, null, DEFAULT_LIMIT)
                .items();

        List<ScoreReportRow> rows = recent.stream()
                .map(r -> new ScoreReportRow(
                        r,
                        jobPostingRepository.findById(r.postingId(), orgId).orElse(null)))
                .toList();

        model.addAttribute("rows", rows);
        model.addAttribute("organizationId", orgId);
        model.addAttribute("username", user.getUsername());
        return "envoy";
    }
}
