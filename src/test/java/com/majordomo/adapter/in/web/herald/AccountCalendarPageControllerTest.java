package com.majordomo.adapter.in.web.herald;

import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.application.herald.CalendarTokenService;
import com.majordomo.application.identity.CurrentOrganizationResolver;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.herald.CalendarToken;
import com.majordomo.domain.model.identity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** Slice tests for the {@code /account/calendar} feed-token management UI (#286). */
@WebMvcTest(AccountCalendarPageController.class)
@Import(SecurityConfig.class)
class AccountCalendarPageControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean CalendarTokenService tokenService;
    @MockitoBean CurrentOrganizationResolver currentOrg;
    @MockitoBean OAuth2UserService oAuth2UserService;
    // Required transitively: SecurityConfig's filter chain injects ApiKeyRepository.
    @MockitoBean com.majordomo.domain.port.out.identity.ApiKeyRepository apiKeyRepository;

    private static final UUID ORG_ID = UuidFactory.newId();
    private User user;

    @BeforeEach
    void seedAuth() {
        user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        when(currentOrg.resolve(any(UserDetails.class)))
                .thenReturn(new CurrentOrganizationResolver.Resolved(user, ORG_ID));
    }

    @Test
    @WithMockUser
    void listRendersTokens() throws Exception {
        var token = new CalendarToken(UuidFactory.newId(), user.getId(), ORG_ID, "hash");
        token.setCreatedAt(Instant.now());
        when(tokenService.listActive(user.getId())).thenReturn(List.of(token));

        mvc.perform(get("/account/calendar"))
                .andExpect(status().isOk())
                .andExpect(view().name("account-calendar"));
    }

    @Test
    @WithMockUser
    void createIssuesTokenAndFlashesSubscriptionUrlOnce() throws Exception {
        when(tokenService.issue(eq(user.getId()), eq(ORG_ID))).thenReturn("rawtoken123");

        MvcResult result = mvc.perform(post("/account/calendar").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/account/calendar"))
                .andReturn();

        Object url = result.getFlashMap().get("feedUrl");
        assertThat(url).isInstanceOf(String.class);
        assertThat((String) url).contains("/herald/calendar/rawtoken123.ics");
        verify(tokenService).issue(user.getId(), ORG_ID);
    }

    @Test
    @WithMockUser
    void revokeDelegatesToServiceScopedToUser() throws Exception {
        UUID tokenId = UuidFactory.newId();

        mvc.perform(post("/account/calendar/{id}/revoke", tokenId).with(csrf()))
                .andExpect(status().is3xxRedirection());

        verify(tokenService).revoke(tokenId, user.getId());
    }
}
