package com.majordomo.adapter.in.web.identity;

import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.application.identity.OrganizationAccessService;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.port.in.identity.ManageUserUseCase;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;
import com.majordomo.domain.port.out.identity.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link UserController}: create user under
 * {@code /api/organizations/{orgId}/users}.
 */
@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean ManageUserUseCase manageUserUseCase;
    @MockitoBean UserRepository userRepository;
    @MockitoBean OrganizationAccessService organizationAccessService;
    @MockitoBean ApiKeyRepository apiKeyRepository;
    @MockitoBean OAuth2UserService oAuth2UserService;

    private static final UUID ORG_ID = UuidFactory.newId();

    /** POST creates a user, verifies access, returns 201 with Location. */
    @Test
    @WithMockUser(username = "admin")
    void createUserReturns201WithLocation() throws Exception {
        UUID callerId = UuidFactory.newId();
        UUID newUserId = UuidFactory.newId();
        User caller = new User(callerId, "admin", "admin@example.com");
        User created = new User(newUserId, "newbie", "newbie@example.com");

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(caller));
        when(manageUserUseCase.createUser(eq("newbie"), eq("newbie@example.com"),
                eq("hunter2"), eq(ORG_ID), eq(callerId))).thenReturn(created);

        mvc.perform(post("/api/organizations/{orgId}/users", ORG_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"newbie","email":"newbie@example.com","password":"hunter2"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        "/api/organizations/" + ORG_ID + "/users/" + newUserId))
                .andExpect(jsonPath("$.id").value(newUserId.toString()))
                .andExpect(jsonPath("$.username").value("newbie"));

        verify(organizationAccessService).verifyAccess(ORG_ID);
    }

    /** Validation rejects blank username with 400. */
    @Test
    @WithMockUser
    void createUserRejectsBlankUsername() throws Exception {
        mvc.perform(post("/api/organizations/{orgId}/users", ORG_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"","email":"e@example.com","password":"x"}
                                """))
                .andExpect(status().isBadRequest());

        verify(manageUserUseCase, never()).createUser(any(), any(), any(), any(), any());
    }

    /** Cross-org access is rejected with 403. */
    @Test
    @WithMockUser(username = "outsider")
    void createUserReturns403WhenAccessDenied() throws Exception {
        doThrow(new AccessDeniedException("denied"))
                .when(organizationAccessService).verifyAccess(ORG_ID);

        mvc.perform(post("/api/organizations/{orgId}/users", ORG_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"x","email":"x@example.com","password":"x"}
                                """))
                .andExpect(status().isForbidden());

        verify(manageUserUseCase, never()).createUser(any(), any(), any(), any(), any());
    }
}
