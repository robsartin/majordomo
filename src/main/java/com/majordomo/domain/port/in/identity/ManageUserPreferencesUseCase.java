package com.majordomo.domain.port.in.identity;

import com.majordomo.domain.model.identity.UserPreferences;

import java.util.UUID;

/**
 * Inbound port for managing user notification preferences.
 */
public interface ManageUserPreferencesUseCase {

    /**
     * Retrieves preferences for the given user, creating defaults if none exist.
     *
     * @param userId the user ID
     * @return the user preferences
     */
    UserPreferences getPreferences(UUID userId);

    /**
     * Updates preferences for the given user.
     *
     * @param userId      the user ID
     * @param preferences the updated preferences
     * @return the saved preferences
     */
    UserPreferences updatePreferences(UUID userId, UserPreferences preferences);
}
