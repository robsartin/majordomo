package com.majordomo.domain.model.ledger;

import java.math.BigDecimal;

/**
 * One row of a spend export: a named property (or the {@code "All properties"}
 * organization rollup) with its purchase price, maintenance cost, and total.
 *
 * @param property        the property name, or "All properties" for the org rollup
 * @param purchasePrice   purchase price (may be zero)
 * @param maintenanceCost total maintenance cost (may be zero)
 * @param totalCost       purchase price plus maintenance cost
 */
public record SpendExportRow(
        String property,
        BigDecimal purchasePrice,
        BigDecimal maintenanceCost,
        BigDecimal totalCost
) {}
