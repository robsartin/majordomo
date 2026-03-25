package com.majordomo.application.identity;

import com.majordomo.domain.model.identity.UserPreferences;
import com.majordomo.domain.port.out.identity.UserPreferencesRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPreferencesServiceTest {

    @Mock
    private UserPreferencesRepository preferencesRepository;

    private UserPreferencesService service;

    @BeforeEach
    void setUp() {
        service = new UserPreferencesService(preferencesRepository);
    }

    @Test
    void getPreferencesReturnsExistingPreferences() {
        var userId = UUID.randomUUID();
        var existing = new UserPreferences();
        existing.setId(UUID.randomUUID());
        existing.setUserId(userId);
        existing.setTimezone("America/New_York");

        when(preferencesRepository.findByUserId(userId))
                .thenReturn(Optional.of(existing));

        var result = service.getPreferences(userId);

        assertEquals("America/New_York", result.getTimezone());
        assertEquals(userId, result.getUserId());
    }

    @Test
    void getPreferencesCreatesDefaultsWhenNoneExist() {
        var userId = UUID.randomUUID();

        when(preferencesRepository.findByUserId(userId))
                .thenReturn(Optional.empty());
        when(preferencesRepository.save(any(UserPreferences.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var result = service.getPreferences(userId);

        assertNotNull(result.getId());
        assertEquals(userId, result.getUserId());
        assertTrue(result.isNotificationsEnabled());
        assertEquals("UTC", result.getTimezone());
        assertEquals("en", result.getLocale());
        assertTrue(result.getNotificationCategoriesDisabled().isEmpty());
    }

    @Test
    void updatePreferencesMergesIntoExisting() {
        var userId = UUID.randomUUID();
        var existingId = UUID.randomUUID();

        var existing = new UserPreferences();
        existing.setId(existingId);
        existing.setUserId(userId);
        existing.setNotificationsEnabled(true);
        existing.setTimezone("UTC");
        existing.setLocale("en");

        var updated = new UserPreferences();
        updated.setNotificationsEnabled(true);
        updated.setNotificationCategoriesDisabled(List.of("SITE_UPDATES"));
        updated.setTimezone("Europe/London");
        updated.setLocale("en-GB");
        updated.setNotificationEmail("custom@example.com");

        when(preferencesRepository.findByUserId(userId))
                .thenReturn(Optional.of(existing));
        when(preferencesRepository.save(any(UserPreferences.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var result = service.updatePreferences(userId, updated);

        assertEquals(existingId, result.getId());
        assertEquals("Europe/London", result.getTimezone());
        assertEquals("en-GB", result.getLocale());
        assertEquals(List.of("SITE_UPDATES"), result.getNotificationCategoriesDisabled());
        assertEquals("custom@example.com", result.getNotificationEmail());

        var captor = ArgumentCaptor.forClass(UserPreferences.class);
        verify(preferencesRepository).save(captor.capture());
        assertNotNull(captor.getValue().getUpdatedAt());
    }
}
