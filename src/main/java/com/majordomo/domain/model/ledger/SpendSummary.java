package com.majordomo.domain.model.ledger;

import java.math.BigDecimal;

/**
 * Summary of spending for a property or organization, including purchase
 * price and total maintenance cost.
 */
public record SpendSummary(
    BigDecimal purchasePrice,
    BigDecimal maintenanceCost,
    BigDecimal totalCost
) {}
