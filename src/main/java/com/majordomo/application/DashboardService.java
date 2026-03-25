package com.majordomo.application;

import com.majordomo.domain.model.DashboardSummary;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.port.in.DashboardUseCase;
import com.majordomo.domain.port.out.concierge.ContactRepository;
import com.majordomo.domain.port.out.herald.MaintenanceScheduleRepository;
import com.majordomo.domain.port.out.herald.ServiceRecordRepository;
import com.majordomo.domain.port.out.ledger.LedgerQueryRepository;
import com.majordomo.domain.port.out.steward.PropertyRepository;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Aggregates data from multiple domain services into a dashboard summary.
 *
 * <p>Collects property and contact counts, filters upcoming and overdue
 * maintenance items to the organization's properties, retrieves recent
 * service records, and calculates total spend.</p>
 */
@Service
public class DashboardService implements DashboardUseCase {

    private static final int UPCOMING_DAYS = 30;
    private static final int RECENT_RECORDS_LIMIT = 10;

    private final PropertyRepository propertyRepository;
    private final ContactRepository contactRepository;
    private final MaintenanceScheduleRepository scheduleRepository;
    private final ServiceRecordRepository serviceRecordRepository;
    private final LedgerQueryRepository ledgerQueryRepository;

    /**
     * Constructs a {@code DashboardService} with the required repository ports.
     *
     * @param propertyRepository      the property repository
     * @param contactRepository       the contact repository
     * @param scheduleRepository      the maintenance schedule repository
     * @param serviceRecordRepository the service record repository
     * @param ledgerQueryRepository   the ledger query repository
     */
    public DashboardService(PropertyRepository propertyRepository,
                            ContactRepository contactRepository,
                            MaintenanceScheduleRepository scheduleRepository,
                            ServiceRecordRepository serviceRecordRepository,
                            LedgerQueryRepository ledgerQueryRepository) {
        this.propertyRepository = propertyRepository;
        this.contactRepository = contactRepository;
        this.scheduleRepository = scheduleRepository;
        this.serviceRecordRepository = serviceRecordRepository;
        this.ledgerQueryRepository = ledgerQueryRepository;
    }

    @Cacheable(value = "dashboard", key = "#organizationId")
    @Override
    public DashboardSummary getSummary(UUID organizationId) {
        var properties = propertyRepository.findByOrganizationId(organizationId);
        var contacts = contactRepository.findByOrganizationId(organizationId);

        Set<UUID> propertyIds = properties.stream()
                .map(Property::getId)
                .collect(Collectors.toSet());

        LocalDate today = LocalDate.now();

        var allDueSoon = scheduleRepository.findDueBefore(today.plusDays(UPCOMING_DAYS))
                .stream().filter(s -> propertyIds.contains(s.getPropertyId())).toList();
        var overdueItems = allDueSoon.stream()
                .filter(s -> s.getNextDue().isBefore(today)).toList();
        var upcomingMaintenance = allDueSoon.stream()
                .filter(s -> !s.getNextDue().isBefore(today)).toList();

        var recentServiceRecords = serviceRecordRepository
                .findRecentByPropertyIds(List.copyOf(propertyIds), RECENT_RECORDS_LIMIT);

        var totalSpend = ledgerQueryRepository
                .totalMaintenanceCostByOrganization(organizationId);

        return new DashboardSummary(
                properties.size(),
                contacts.size(),
                upcomingMaintenance,
                overdueItems,
                recentServiceRecords,
                totalSpend
        );
    }
}
