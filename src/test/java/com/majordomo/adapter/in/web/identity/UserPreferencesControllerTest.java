package com.majordomo.adapter.in.web.identity;

import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.identity.UserPreferences;
import com.majordomo.domain.port.in.identity.ManageUserPreferencesUseCase;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link UserPreferencesController}: get / update under
 * {@code /api/users/{userId}/preferences}.
 */
@WebMvcTest(UserPreferencesController.class)
@Import(SecurityConfig.class)
class UserPreferencesControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean ManageUserPreferencesUseCase useCase;
    @MockitoBean ApiKeyRepository apiKeyRepository;
    @MockitoBean OAuth2UserService oAuth2UserService;

    private static final UUID USER_ID = UuidFactory.newId();

    /** GET returns the user's preferences. */
    @Test
    @WithMockUser
    void getReturnsPreferences() throws Exception {
        UserPreferences prefs = prefs(USER_ID, "alice@example.com", true);
        when(useCase.getPreferences(USER_ID)).thenReturn(prefs);

        mvc.perform(get("/api/users/{userId}/preferences", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.notificationEmail").value("alice@example.com"))
                .andExpect(jsonPath("$.notificationsEnabled").value(true));

        verify(useCase).getPreferences(USER_ID);
    }

    /** PUT updates the user's preferences and returns the saved value. */
    @Test
    @WithMockUser
    void updateReturnsSavedPreferences() throws Exception {
        UserPreferences saved = prefs(USER_ID, "alice@example.com", false);
        when(useCase.updatePreferences(eq(USER_ID), any(UserPreferences.class))).thenReturn(saved);

        mvc.perform(put("/api/users/{userId}/preferences", USER_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "%s",
                                  "notificationEmail": "alice@example.com",
                                  "notificationsEnabled": false
                                }
                                """.formatted(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationsEnabled").value(false));

        verify(useCase).updatePreferences(eq(USER_ID), any(UserPreferences.class));
    }

    private static UserPreferences prefs(UUID userId, String email, boolean enabled) {
        UserPreferences p = new UserPreferences();
        p.setId(UuidFactory.newId());
        p.setUserId(userId);
        p.setNotificationEmail(email);
        p.setNotificationsEnabled(enabled);
        return p;
    }
}
