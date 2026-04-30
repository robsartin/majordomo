package com.majordomo.adapter.in.web;

import com.majordomo.adapter.in.web.config.OrgContext;
import com.majordomo.domain.port.in.DashboardUseCase;
import com.majordomo.domain.port.in.envoy.GetRecentApplyNowPostingsUseCase;
import com.majordomo.domain.port.in.ledger.QuerySpendUseCase;

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
    private final GetRecentApplyNowPostingsUseCase recentApplyNowUseCase;
    private final QuerySpendUseCase spendUseCase;

    /**
     * Constructs the dashboard page controller.
     *
     * @param dashboardUseCase      the inbound port for dashboard data retrieval
     * @param recentApplyNowUseCase inbound port for recent APPLY_NOW postings
     * @param spendUseCase          inbound port for ledger spend queries
     */
    public DashboardPageController(DashboardUseCase dashboardUseCase,
                                   GetRecentApplyNowPostingsUseCase recentApplyNowUseCase,
                                   QuerySpendUseCase spendUseCase) {
        this.dashboardUseCase = dashboardUseCase;
        this.recentApplyNowUseCase = recentApplyNowUseCase;
        this.spendUseCase = spendUseCase;
    }

    /**
     * Renders the dashboard page for the authenticated user's organization.
     *
     * @param orgContext authenticated user + organization, resolved by
     *                   {@link com.majordomo.adapter.in.web.config.OrgContextArgumentResolver}
     * @param model      the Thymeleaf model
     * @return the dashboard template name
     */
    @GetMapping("/dashboard")
    public String dashboard(OrgContext orgContext, Model model) {
        var orgId = orgContext.organizationId();
        model.addAttribute("summary", dashboardUseCase.getSummary(orgId));
        model.addAttribute("username", orgContext.username());
        model.addAttribute("applyNowPostings",
                recentApplyNowUseCase.getRecentApplyNow(orgId, APPLY_NOW_PANEL_LIMIT));
        model.addAttribute("projectedAnnualSpend", spendUseCase.projectedAnnualSpend(orgId));
        return "dashboard";
    }
}
