package com.majordomo.adapter.out.persistence.identity;

import com.majordomo.domain.model.identity.UserPreferences;

import java.util.ArrayList;

final class UserPreferencesMapper {

    private UserPreferencesMapper() { }

    static UserPreferencesEntity toEntity(UserPreferences prefs) {
        var entity = new UserPreferencesEntity();
        entity.setId(prefs.getId());
        entity.setUserId(prefs.getUserId());
        entity.setNotificationEmail(prefs.getNotificationEmail());
        entity.setNotificationsEnabled(prefs.isNotificationsEnabled());
        entity.setNotificationCategoriesDisabled(
                prefs.getNotificationCategoriesDisabled() != null
                        ? new ArrayList<>(prefs.getNotificationCategoriesDisabled())
                        : new ArrayList<>());
        entity.setTimezone(prefs.getTimezone());
        entity.setLocale(prefs.getLocale());
        entity.setCreatedAt(prefs.getCreatedAt());
        entity.setUpdatedAt(prefs.getUpdatedAt());
        return entity;
    }

    static UserPreferences toDomain(UserPreferencesEntity entity) {
        var prefs = new UserPreferences();
        prefs.setId(entity.getId());
        prefs.setUserId(entity.getUserId());
        prefs.setNotificationEmail(entity.getNotificationEmail());
        prefs.setNotificationsEnabled(entity.isNotificationsEnabled());
        prefs.setNotificationCategoriesDisabled(
                entity.getNotificationCategoriesDisabled() != null
                        ? new ArrayList<>(entity.getNotificationCategoriesDisabled())
                        : new ArrayList<>());
        prefs.setTimezone(entity.getTimezone());
        prefs.setLocale(entity.getLocale());
        prefs.setCreatedAt(entity.getCreatedAt());
        prefs.setUpdatedAt(entity.getUpdatedAt());
        return prefs;
    }
}
