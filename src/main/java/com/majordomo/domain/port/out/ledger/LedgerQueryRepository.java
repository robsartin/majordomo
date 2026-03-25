package com.majordomo.domain.port.out.ledger;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Outbound port for querying financial data across properties and service records.
 */
public interface LedgerQueryRepository {

    /**
     * Calculates the total maintenance cost for a property.
     *
     * @param propertyId the property ID
     * @return the sum of all service record costs, or zero if none
     */
    BigDecimal totalMaintenanceCost(UUID propertyId);

    /**
     * Calculates the total maintenance cost across all properties in an organization.
     *
     * @param organizationId the organization ID
     * @return the sum of all service record costs for the org, or zero
     */
    BigDecimal totalMaintenanceCostByOrganization(UUID organizationId);
}
