package com.majordomo.adapter.in.web;

import com.majordomo.application.identity.CurrentOrganizationResolver;
import com.majordomo.domain.port.in.DashboardUseCase;
import com.majordomo.domain.port.in.envoy.GetRecentApplyNowPostingsUseCase;
import com.majordomo.domain.port.in.ledger.QuerySpendUseCase;

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
    private final CurrentOrganizationResolver currentOrg;
    private final GetRecentApplyNowPostingsUseCase recentApplyNowUseCase;
    private final QuerySpendUseCase spendUseCase;

    /**
     * Constructs the dashboard page controller.
     *
     * @param dashboardUseCase      the inbound port for dashboard data retrieval
     * @param currentOrg            resolves the authenticated user's first organization
     * @param recentApplyNowUseCase inbound port for recent APPLY_NOW postings
     * @param spendUseCase          inbound port for ledger spend queries
     */
    public DashboardPageController(DashboardUseCase dashboardUseCase,
                                   CurrentOrganizationResolver currentOrg,
                                   GetRecentApplyNowPostingsUseCase recentApplyNowUseCase,
                                   QuerySpendUseCase spendUseCase) {
        this.dashboardUseCase = dashboardUseCase;
        this.currentOrg = currentOrg;
        this.recentApplyNowUseCase = recentApplyNowUseCase;
        this.spendUseCase = spendUseCase;
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
        var resolved = currentOrg.resolve(principal);
        if (resolved.organizationId() == null) {
            return "redirect:/";
        }
        var orgId = resolved.organizationId();
        var summary = dashboardUseCase.getSummary(orgId);
        model.addAttribute("summary", summary);
        model.addAttribute("username", resolved.user().getUsername());
        model.addAttribute("applyNowPostings",
                recentApplyNowUseCase.getRecentApplyNow(orgId, APPLY_NOW_PANEL_LIMIT));
        model.addAttribute("projectedAnnualSpend", spendUseCase.projectedAnnualSpend(orgId));
        return "dashboard";
    }
}
