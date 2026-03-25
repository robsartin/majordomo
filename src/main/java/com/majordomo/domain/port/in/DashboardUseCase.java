package com.majordomo.domain.port.in;

import com.majordomo.domain.model.DashboardSummary;

import java.util.UUID;

/**
 * Inbound port for retrieving an organization's dashboard summary.
 */
public interface DashboardUseCase {

    /**
     * Returns the dashboard summary for an organization.
     *
     * @param organizationId the organization ID
     * @return aggregated dashboard data
     */
    DashboardSummary getSummary(UUID organizationId);
}
