package com.majordomo.application.ledger;

import com.majordomo.domain.model.herald.Frequency;
import com.majordomo.domain.model.herald.MaintenanceSchedule;
import com.majordomo.domain.model.ledger.SpendSummary;
import com.majordomo.domain.port.in.ledger.QuerySpendUseCase;
import com.majordomo.domain.port.out.herald.MaintenanceScheduleRepository;
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

    private static final int DAYS_IN_YEAR = 365;

    private final PropertyRepository propertyRepository;
    private final LedgerQueryRepository ledgerQueryRepository;
    private final MaintenanceScheduleRepository maintenanceScheduleRepository;

    /**
     * Constructs the service with required repositories.
     *
     * @param propertyRepository            port for loading property details
     * @param ledgerQueryRepository         port for aggregating maintenance costs
     * @param maintenanceScheduleRepository port for loading maintenance schedules
     */
    public LedgerService(PropertyRepository propertyRepository,
                          LedgerQueryRepository ledgerQueryRepository,
                          MaintenanceScheduleRepository maintenanceScheduleRepository) {
        this.propertyRepository = propertyRepository;
        this.ledgerQueryRepository = ledgerQueryRepository;
        this.maintenanceScheduleRepository = maintenanceScheduleRepository;
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

    @Override
    public BigDecimal projectedAnnualSpend(UUID organizationId) {
        return propertyRepository.findByOrganizationId(organizationId).stream()
                .flatMap(p -> maintenanceScheduleRepository
                        .findByPropertyId(p.getId()).stream())
                .filter(s -> s.getEstimatedCost() != null)
                .map(this::annualCostForSchedule)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Computes the annual projected cost for a single maintenance schedule.
     *
     * @param schedule the maintenance schedule with a non-null estimated cost
     * @return the estimated cost multiplied by the number of occurrences per year
     */
    private BigDecimal annualCostForSchedule(MaintenanceSchedule schedule) {
        int occurrences = occurrencesPerYear(schedule);
        return schedule.getEstimatedCost()
                .multiply(BigDecimal.valueOf(occurrences));
    }

    /**
     * Returns the number of occurrences per year for the given schedule's frequency.
     *
     * @param schedule the maintenance schedule
     * @return occurrences per year (rounded up for CUSTOM frequency)
     */
    private int occurrencesPerYear(MaintenanceSchedule schedule) {
        if (schedule.getFrequency() == Frequency.CUSTOM) {
            int intervalDays = schedule.getCustomIntervalDays() != null
                    ? schedule.getCustomIntervalDays() : 1;
            return (int) Math.ceil((double) DAYS_IN_YEAR / intervalDays);
        }
        return switch (schedule.getFrequency()) {
            case WEEKLY -> 52;
            case MONTHLY -> 12;
            case QUARTERLY -> 4;
            case SEMI_ANNUAL -> 2;
            case ANNUAL -> 1;
            default -> 1;
        };
    }
}
