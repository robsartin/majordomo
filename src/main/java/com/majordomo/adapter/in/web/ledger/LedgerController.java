package com.majordomo.adapter.in.web.ledger;

import com.majordomo.domain.model.ledger.SpendSummary;
import com.majordomo.domain.port.in.ledger.QuerySpendUseCase;

import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for the Ledger domain: queries spend data for properties
 * and organizations.
 */
@RestController
@RequestMapping("/api/ledger")
@Tag(name = "Ledger", description = "Finance and spend tracking")
public class LedgerController {

    private final QuerySpendUseCase querySpendUseCase;

    /**
     * Constructs the controller with the spend query use case.
     *
     * @param querySpendUseCase the inbound port for spend queries
     */
    public LedgerController(QuerySpendUseCase querySpendUseCase) {
        this.querySpendUseCase = querySpendUseCase;
    }

    /**
     * Returns a spend summary for a single property.
     *
     * @param propertyId the property UUID
     * @return the spend summary including purchase price and maintenance costs
     */
    @GetMapping("/properties/{propertyId}/spend")
    public SpendSummary spendForProperty(@PathVariable UUID propertyId) {
        return querySpendUseCase.spendForProperty(propertyId);
    }

    /**
     * Returns a spend summary for all properties in an organization.
     *
     * @param organizationId the organization UUID
     * @return the aggregated spend summary
     */
    @GetMapping("/organizations/{organizationId}/spend")
    public SpendSummary spendForOrganization(
            @PathVariable UUID organizationId) {
        return querySpendUseCase.spendForOrganization(organizationId);
    }
}
