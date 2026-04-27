package com.majordomo.adapter.in.web.envoy;

import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.port.in.envoy.QueryScoreReportsUseCase;
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
 * score reports for the authenticated user's first organization.
 */
@Controller
public class EnvoyPageController {

    private static final int DEFAULT_LIMIT = 50;

    private final QueryScoreReportsUseCase reports;
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;

    /**
     * Constructs the controller.
     *
     * @param reports              inbound port for report queries
     * @param userRepository       outbound port for user lookups
     * @param membershipRepository outbound port for membership lookups
     */
    public EnvoyPageController(QueryScoreReportsUseCase reports,
                               UserRepository userRepository,
                               MembershipRepository membershipRepository) {
        this.reports = reports;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
    }

    /**
     * Renders the Envoy report list for the authenticated user's first
     * organization. Redirects home if the user has no membership.
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

        model.addAttribute("reports", recent);
        model.addAttribute("organizationId", orgId);
        model.addAttribute("username", user.getUsername());
        return "envoy";
    }
}
