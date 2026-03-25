package com.majordomo.application.ledger;

import com.majordomo.domain.model.ledger.SpendSummary;
import com.majordomo.domain.port.in.ledger.QuerySpendUseCase;
import com.majordomo.domain.port.out.ledger.LedgerQueryRepository;
import com.majordomo.domain.port.out.steward.PropertyRepository;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Application service for ledger queries, computing spend summaries
 * by combining property purchase prices with maintenance costs.
 */
@Service
public class LedgerService implements QuerySpendUseCase {

    private final PropertyRepository propertyRepository;
    private final LedgerQueryRepository ledgerQueryRepository;

    /**
     * Constructs the service with required repositories.
     *
     * @param propertyRepository    port for loading property details
     * @param ledgerQueryRepository port for aggregating maintenance costs
     */
    public LedgerService(PropertyRepository propertyRepository,
                          LedgerQueryRepository ledgerQueryRepository) {
        this.propertyRepository = propertyRepository;
        this.ledgerQueryRepository = ledgerQueryRepository;
    }

    @Override
    @Cacheable(value = "spend", key = "#propertyId")
    public SpendSummary spendForProperty(UUID propertyId) {
        var property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Property not found: " + propertyId));
        BigDecimal purchasePrice = property.getPurchasePrice() != null
                ? property.getPurchasePrice() : BigDecimal.ZERO;
        BigDecimal maintenanceCost =
                ledgerQueryRepository.totalMaintenanceCost(propertyId);
        return new SpendSummary(purchasePrice, maintenanceCost,
                purchasePrice.add(maintenanceCost));
    }

    @Override
    @Cacheable(value = "spend", key = "'org:' + #organizationId")
    public SpendSummary spendForOrganization(UUID organizationId) {
        var properties =
                propertyRepository.findByOrganizationId(organizationId);
        BigDecimal purchasePrice = properties.stream()
                .map(p -> p.getPurchasePrice() != null
                        ? p.getPurchasePrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal maintenanceCost =
                ledgerQueryRepository.totalMaintenanceCostByOrganization(
                        organizationId);
        return new SpendSummary(purchasePrice, maintenanceCost,
                purchasePrice.add(maintenanceCost));
    }
}
