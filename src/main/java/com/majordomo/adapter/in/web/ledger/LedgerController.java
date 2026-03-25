package com.majordomo.adapter.in.web.ledger;

import com.majordomo.application.identity.OrganizationAccessService;
import com.majordomo.domain.model.EntityNotFoundException;
import com.majordomo.domain.model.ledger.SpendSummary;
import com.majordomo.domain.port.in.ledger.QuerySpendUseCase;
import com.majordomo.domain.port.out.steward.PropertyRepository;

import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
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
    private final OrganizationAccessService organizationAccessService;
    private final PropertyRepository propertyRepository;

    /**
     * Constructs the controller with the required dependencies.
     *
     * @param querySpendUseCase         the inbound port for spend queries
     * @param organizationAccessService the service for verifying organization membership
     * @param propertyRepository        the repository for looking up property ownership
     */
    public LedgerController(QuerySpendUseCase querySpendUseCase,
                            OrganizationAccessService organizationAccessService,
                            PropertyRepository propertyRepository) {
        this.querySpendUseCase = querySpendUseCase;
        this.organizationAccessService = organizationAccessService;
        this.propertyRepository = propertyRepository;
    }

    /**
     * Returns a spend summary for a single property.
     *
     * @param propertyId the property UUID
     * @return the spend summary including purchase price and maintenance costs
     */
    @GetMapping("/properties/{propertyId}/spend")
    public SpendSummary spendForProperty(@PathVariable UUID propertyId) {
        var property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new EntityNotFoundException("Property", propertyId));
        organizationAccessService.verifyAccess(property.getOrganizationId());
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
        organizationAccessService.verifyAccess(organizationId);
        return querySpendUseCase.spendForOrganization(organizationId);
    }

    /**
     * Returns the projected annual maintenance spend for an organization.
     *
     * @param organizationId the organization UUID
     * @return projected annual cost based on schedule frequencies and estimated costs
     */
    @GetMapping("/organizations/{organizationId}/projected-annual")
    public BigDecimal projectedAnnualSpend(@PathVariable UUID organizationId) {
        organizationAccessService.verifyAccess(organizationId);
        return querySpendUseCase.projectedAnnualSpend(organizationId);
    }
}
