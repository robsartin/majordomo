package com.majordomo.application.identity;

import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.identity.UserPreferences;
import com.majordomo.domain.port.in.identity.ManageUserPreferencesUseCase;
import com.majordomo.domain.port.out.identity.UserPreferencesRepository;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Application service for managing user notification preferences.
 * Creates sensible defaults when no preferences exist for a user.
 */
@Service
public class UserPreferencesService implements ManageUserPreferencesUseCase {

    private final UserPreferencesRepository preferencesRepository;

    /**
     * Constructs the service with the required repository.
     *
     * @param preferencesRepository the outbound port for persisting preferences
     */
    public UserPreferencesService(UserPreferencesRepository preferencesRepository) {
        this.preferencesRepository = preferencesRepository;
    }

    @Override
    public UserPreferences getPreferences(UUID userId) {
        return preferencesRepository.findByUserId(userId)
                .orElseGet(() -> createDefaults(userId));
    }

    @Override
    public UserPreferences updatePreferences(UUID userId, UserPreferences preferences) {
        var existing = preferencesRepository.findByUserId(userId)
                .orElseGet(() -> createDefaults(userId));

        existing.setNotificationEmail(preferences.getNotificationEmail());
        existing.setNotificationsEnabled(preferences.isNotificationsEnabled());
        existing.setNotificationCategoriesDisabled(
                preferences.getNotificationCategoriesDisabled() != null
                        ? preferences.getNotificationCategoriesDisabled()
                        : new ArrayList<>());
        existing.setTimezone(preferences.getTimezone() != null
                ? preferences.getTimezone() : "UTC");
        existing.setLocale(preferences.getLocale() != null
                ? preferences.getLocale() : "en");
        existing.setUpdatedAt(Instant.now());

        return preferencesRepository.save(existing);
    }

    private UserPreferences createDefaults(UUID userId) {
        var prefs = new UserPreferences();
        prefs.setId(UuidFactory.newId());
        prefs.setUserId(userId);
        prefs.setNotificationsEnabled(true);
        prefs.setNotificationCategoriesDisabled(new ArrayList<>());
        prefs.setTimezone("UTC");
        prefs.setLocale("en");
        prefs.setCreatedAt(Instant.now());
        prefs.setUpdatedAt(Instant.now());
        return preferencesRepository.save(prefs);
    }
}
