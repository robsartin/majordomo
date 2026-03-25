package com.majordomo.domain.model.identity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * User-specific preferences for notifications, timezone, and locale.
 */
public class UserPreferences {

    private UUID id;
    private UUID userId;
    private String notificationEmail;
    private boolean notificationsEnabled;
    private List<String> notificationCategoriesDisabled;
    private String timezone;
    private String locale;
    private Instant createdAt;
    private Instant updatedAt;

    public UserPreferences() { }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getNotificationEmail() { return notificationEmail; }
    public void setNotificationEmail(String notificationEmail) { this.notificationEmail = notificationEmail; }

    public boolean isNotificationsEnabled() { return notificationsEnabled; }
    public void setNotificationsEnabled(boolean notificationsEnabled) {
        this.notificationsEnabled = notificationsEnabled;
    }

    public List<String> getNotificationCategoriesDisabled() { return notificationCategoriesDisabled; }
    public void setNotificationCategoriesDisabled(List<String> notificationCategoriesDisabled) {
        this.notificationCategoriesDisabled = notificationCategoriesDisabled;
    }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    /**
     * Checks if notifications are enabled for the given category.
     *
     * @param category the notification category
     * @return true if enabled
     */
    public boolean isCategoryEnabled(NotificationCategory category) {
        return notificationsEnabled
            && (notificationCategoriesDisabled == null
                || !notificationCategoriesDisabled.contains(category.name()));
    }
}
