package com.majordomo.adapter.in.web;

import com.majordomo.domain.port.in.DashboardUseCase;
import com.majordomo.domain.port.in.envoy.GetRecentApplyNowPostingsUseCase;
import com.majordomo.domain.port.out.identity.MembershipRepository;
import com.majordomo.domain.port.out.identity.UserRepository;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the web dashboard page showing an organization overview.
 */
@Controller
public class DashboardPageController {

    private static final int APPLY_NOW_PANEL_LIMIT = 5;

    private final DashboardUseCase dashboardUseCase;
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final GetRecentApplyNowPostingsUseCase recentApplyNowUseCase;

    /**
     * Constructs the dashboard page controller.
     *
     * @param dashboardUseCase      the inbound port for dashboard data retrieval
     * @param userRepository        the outbound port for user lookups
     * @param membershipRepository  the outbound port for membership lookups
     * @param recentApplyNowUseCase inbound port for recent APPLY_NOW postings
     */
    public DashboardPageController(DashboardUseCase dashboardUseCase,
                                   UserRepository userRepository,
                                   MembershipRepository membershipRepository,
                                   GetRecentApplyNowPostingsUseCase recentApplyNowUseCase) {
        this.dashboardUseCase = dashboardUseCase;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.recentApplyNowUseCase = recentApplyNowUseCase;
    }

    /**
     * Renders the dashboard page for the authenticated user's organization.
     *
     * @param principal the authenticated user
     * @param model     the Thymeleaf model
     * @return the dashboard template name, or a redirect if the user has no organization
     */
    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal UserDetails principal, Model model) {
        var user = userRepository.findByUsername(principal.getUsername()).orElseThrow();
        var memberships = membershipRepository.findByUserId(user.getId());
        if (memberships.isEmpty()) {
            return "redirect:/";
        }
        var orgId = memberships.get(0).getOrganizationId();
        var summary = dashboardUseCase.getSummary(orgId);
        model.addAttribute("summary", summary);
        model.addAttribute("username", user.getUsername());
        model.addAttribute("applyNowPostings",
                recentApplyNowUseCase.getRecentApplyNow(orgId, APPLY_NOW_PANEL_LIMIT));
        return "dashboard";
    }
}
