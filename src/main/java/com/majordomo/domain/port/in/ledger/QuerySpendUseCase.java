package com.majordomo.domain.port.in.ledger;

import com.majordomo.domain.model.ledger.SpendSummary;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Inbound port for querying spend data across properties and organizations.
 */
public interface QuerySpendUseCase {

    /**
     * Returns a spend summary for a single property.
     *
     * @param propertyId the property ID
     * @return the spend summary including purchase price and maintenance costs
     */
    SpendSummary spendForProperty(UUID propertyId);

    /**
     * Returns a spend summary for all properties in an organization.
     *
     * @param organizationId the organization ID
     * @return the aggregated spend summary
     */
    SpendSummary spendForOrganization(UUID organizationId);

    /**
     * Returns projected annual maintenance spend for an organization.
     *
     * @param organizationId the organization ID
     * @return projected annual cost based on schedule frequencies and estimated costs
     */
    BigDecimal projectedAnnualSpend(UUID organizationId);
}
