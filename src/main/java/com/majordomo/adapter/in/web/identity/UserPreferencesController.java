package com.majordomo.adapter.in.web.identity;

import com.majordomo.domain.model.identity.UserPreferences;
import com.majordomo.domain.port.in.identity.ManageUserPreferencesUseCase;

import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for managing user notification preferences.
 */
@RestController
@RequestMapping("/api/users/{userId}/preferences")
@Tag(name = "Identity", description = "User preferences management")
public class UserPreferencesController {

    private final ManageUserPreferencesUseCase useCase;

    /**
     * Constructs the controller with the required use case.
     *
     * @param useCase the inbound port for managing preferences
     */
    public UserPreferencesController(ManageUserPreferencesUseCase useCase) {
        this.useCase = useCase;
    }

    /**
     * Retrieves the preferences for a user, creating defaults if none exist.
     *
     * @param userId the user ID
     * @return the user preferences
     */
    @GetMapping
    public ResponseEntity<UserPreferences> getPreferences(@PathVariable UUID userId) {
        return ResponseEntity.ok(useCase.getPreferences(userId));
    }

    /**
     * Updates the preferences for a user.
     *
     * @param userId      the user ID
     * @param preferences the updated preferences
     * @return the saved preferences
     */
    @PutMapping
    public ResponseEntity<UserPreferences> updatePreferences(
            @PathVariable UUID userId,
            @RequestBody UserPreferences preferences) {
        return ResponseEntity.ok(useCase.updatePreferences(userId, preferences));
    }
}
