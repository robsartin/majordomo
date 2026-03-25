package com.majordomo.domain.model.herald;

/**
 * Defines how often a {@link MaintenanceSchedule} recurs.
 * Use {@code CUSTOM} together with {@code customIntervalDays} for non-standard intervals.
 */
public enum Frequency {
    WEEKLY,
    MONTHLY,
    QUARTERLY,
    SEMI_ANNUAL,
    ANNUAL,
    CUSTOM
}
