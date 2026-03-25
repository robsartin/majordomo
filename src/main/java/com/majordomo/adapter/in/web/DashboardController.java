package com.majordomo.adapter.in.web;

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

    /**
     * Constructs a {@code DashboardController} with the required use case.
     *
     * @param dashboardUseCase the inbound port for dashboard data retrieval
     */
    public DashboardController(DashboardUseCase dashboardUseCase) {
        this.dashboardUseCase = dashboardUseCase;
    }

    /**
     * Returns the aggregated dashboard summary for the specified organization.
     *
     * @param organizationId the UUID of the organization
     * @return the dashboard summary containing counts, maintenance items, and spend
     */
    @GetMapping
    public DashboardSummary getSummary(@RequestParam UUID organizationId) {
        return dashboardUseCase.getSummary(organizationId);
    }
}
