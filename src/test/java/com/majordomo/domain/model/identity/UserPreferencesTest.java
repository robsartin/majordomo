package com.majordomo.domain.model.identity;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserPreferencesTest {

    @Test
    void isCategoryEnabledReturnsTrueWhenAllEnabled() {
        var prefs = new UserPreferences();
        prefs.setNotificationsEnabled(true);
        prefs.setNotificationCategoriesDisabled(new ArrayList<>());

        assertTrue(prefs.isCategoryEnabled(NotificationCategory.MAINTENANCE_DUE));
        assertTrue(prefs.isCategoryEnabled(NotificationCategory.WARRANTY_EXPIRING));
        assertTrue(prefs.isCategoryEnabled(NotificationCategory.SITE_UPDATES));
    }

    @Test
    void isCategoryEnabledReturnsFalseWhenGloballyDisabled() {
        var prefs = new UserPreferences();
        prefs.setNotificationsEnabled(false);
        prefs.setNotificationCategoriesDisabled(new ArrayList<>());

        assertFalse(prefs.isCategoryEnabled(NotificationCategory.MAINTENANCE_DUE));
        assertFalse(prefs.isCategoryEnabled(NotificationCategory.SITE_UPDATES));
    }

    @Test
    void isCategoryEnabledReturnsFalseForDisabledCategory() {
        var prefs = new UserPreferences();
        prefs.setNotificationsEnabled(true);
        prefs.setNotificationCategoriesDisabled(List.of("SITE_UPDATES"));

        assertTrue(prefs.isCategoryEnabled(NotificationCategory.MAINTENANCE_DUE));
        assertFalse(prefs.isCategoryEnabled(NotificationCategory.SITE_UPDATES));
    }

    @Test
    void isCategoryEnabledReturnsFalseWhenMaintenanceDueDisabled() {
        var prefs = new UserPreferences();
        prefs.setNotificationsEnabled(true);
        prefs.setNotificationCategoriesDisabled(List.of("MAINTENANCE_DUE"));

        assertFalse(prefs.isCategoryEnabled(NotificationCategory.MAINTENANCE_DUE));
        assertTrue(prefs.isCategoryEnabled(NotificationCategory.WARRANTY_EXPIRING));
    }

    @Test
    void isCategoryEnabledReturnsTrueWhenDisabledListIsNull() {
        var prefs = new UserPreferences();
        prefs.setNotificationsEnabled(true);
        prefs.setNotificationCategoriesDisabled(null);

        assertTrue(prefs.isCategoryEnabled(NotificationCategory.MAINTENANCE_DUE));
        assertTrue(prefs.isCategoryEnabled(NotificationCategory.SITE_UPDATES));
    }

    @Test
    void isCategoryEnabledReturnsFalseWhenMultipleCategoriesDisabled() {
        var prefs = new UserPreferences();
        prefs.setNotificationsEnabled(true);
        prefs.setNotificationCategoriesDisabled(
                List.of("MAINTENANCE_DUE", "WARRANTY_EXPIRING", "SITE_UPDATES"));

        assertFalse(prefs.isCategoryEnabled(NotificationCategory.MAINTENANCE_DUE));
        assertFalse(prefs.isCategoryEnabled(NotificationCategory.WARRANTY_EXPIRING));
        assertFalse(prefs.isCategoryEnabled(NotificationCategory.SITE_UPDATES));
    }
}
