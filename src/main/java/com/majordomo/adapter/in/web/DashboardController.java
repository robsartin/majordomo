package com.majordomo.adapter.in.web;

import com.majordomo.application.identity.OrganizationAccessService;
import com.majordomo.domain.model.DashboardSummary;
import com.majordomo.domain.port.in.DashboardUseCase;

import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller that exposes the dashboard summary endpoint.
 *
 * <p>Acts as an inbound adapter in the hexagonal architecture, delegating to
 * {@link DashboardUseCase} for data aggregation.</p>
 */
@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "Organization overview")
public class DashboardController {

    private final DashboardUseCase dashboardUseCase;
    private final OrganizationAccessService organizationAccessService;

    /**
     * Constructs a {@code DashboardController} with the required dependencies.
     *
     * @param dashboardUseCase           the inbound port for dashboard data retrieval
     * @param organizationAccessService  the service for verifying organization membership
     */
    public DashboardController(DashboardUseCase dashboardUseCase,
                               OrganizationAccessService organizationAccessService) {
        this.dashboardUseCase = dashboardUseCase;
        this.organizationAccessService = organizationAccessService;
    }

    /**
     * Returns the aggregated dashboard summary for the specified organization.
     *
     * @param organizationId the UUID of the organization
     * @return the dashboard summary containing counts, maintenance items, and spend
     */
    @GetMapping
    public DashboardSummary getSummary(@RequestParam UUID organizationId) {
        organizationAccessService.verifyAccess(organizationId);
        return dashboardUseCase.getSummary(organizationId);
    }
}
