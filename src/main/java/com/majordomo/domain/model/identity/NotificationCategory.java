package com.majordomo.domain.model.identity;

/**
 * Categories of notifications that can be individually disabled per user.
 */
public enum NotificationCategory {
    /** High-scoring job posting alerts (envoy APPLY_NOW). */
    HIGH_SCORE_POSTING,
    /** Upcoming maintenance reminders. */
    MAINTENANCE_DUE,
    /** System announcements and new features. */
    SITE_UPDATES,
    /** Property warranty expiration alerts. */
    WARRANTY_EXPIRING
}
