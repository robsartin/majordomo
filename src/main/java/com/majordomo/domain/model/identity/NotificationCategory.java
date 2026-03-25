package com.majordomo.domain.model.identity;

/**
 * Categories of notifications that can be individually disabled per user.
 */
public enum NotificationCategory {
    /** Upcoming maintenance reminders. */
    MAINTENANCE_DUE,
    /** Property warranty expiration alerts. */
    WARRANTY_EXPIRING,
    /** System announcements and new features. */
    SITE_UPDATES
}
