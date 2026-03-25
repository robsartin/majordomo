package com.majordomo.domain.port.out.identity;

import com.majordomo.domain.model.identity.UserPreferences;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for persisting and retrieving user notification preferences.
 */
public interface UserPreferencesRepository {

    /**
     * Persists user preferences, inserting or updating as needed.
     *
     * @param preferences the preferences to save
     * @return the saved preferences, including any generated or updated fields
     */
    UserPreferences save(UserPreferences preferences);

    /**
     * Retrieves preferences for a given user.
     *
     * @param userId the user ID
     * @return the preferences, or empty if none have been created yet
     */
    Optional<UserPreferences> findByUserId(UUID userId);
}
