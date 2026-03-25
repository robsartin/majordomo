package com.majordomo.domain.model;

import com.majordomo.domain.model.herald.MaintenanceSchedule;
import com.majordomo.domain.model.herald.ServiceRecord;

import java.math.BigDecimal;
import java.util.List;

/**
 * Aggregated overview of an organization's properties, contacts,
 * maintenance status, and spending.
 */
public record DashboardSummary(
    int propertyCount,
    int contactCount,
    List<MaintenanceSchedule> upcomingMaintenance,
    List<MaintenanceSchedule> overdueItems,
    List<ServiceRecord> recentServiceRecords,
    BigDecimal totalSpend
) { }
