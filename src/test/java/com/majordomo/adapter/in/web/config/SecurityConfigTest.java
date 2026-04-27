package com.majordomo.adapter.in.web.config;

import com.majordomo.adapter.in.web.HomeController;
import com.majordomo.adapter.in.web.concierge.ContactController;
import com.majordomo.adapter.in.web.identity.LoginController;
import com.majordomo.application.identity.OrganizationAccessService;
import com.majordomo.domain.port.in.concierge.ManageContactUseCase;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for {@link SecurityConfig} verifying public and protected endpoint access.
 */
@WebMvcTest(controllers = {HomeController.class, LoginController.class, ContactController.class})
@Import({SecurityConfig.class, SecurityConfigTest.TestUserDetailsConfig.class})
class SecurityConfigTest {

    /**
     * Provides an in-memory user details service so the {@code formLogin}
     * request builder can authenticate against a known principal without
     * needing the production {@code AuthenticationService} (which requires
     * the full domain wiring).
     */
    @TestConfiguration
    static class TestUserDetailsConfig {
        @Bean
        UserDetailsService userDetailsService() {
            String hash = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()
                    .encode("secret");
            return new InMemoryUserDetailsManager(
                    User.withUsername("alice")
                            .password(hash)
                            .authorities("ROLE_USER")
                            .build());
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApiKeyRepository apiKeyRepository;

    @MockitoBean
    private OAuth2UserService oAuth2UserService;

    @MockitoBean
    private ManageContactUseCase contactUseCase;

    @MockitoBean
    private OrganizationAccessService organizationAccessService;

    /** Unauthenticated access to root URL should return 200. */
    @Test
    void rootUrlIsAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());
    }

    /** Unauthenticated access to login page should return 200. */
    @Test
    void loginPageIsAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());
    }

    /** Unauthenticated access to API should redirect to login (302). */
    @Test
    void apiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/contacts"))
                .andExpect(status().is3xxRedirection());
    }

    /** Favicon is publicly accessible — no auth wall, no 500 from a missing static resource. */
    @Test
    void faviconIsAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/favicon.ico"))
                .andExpect(status().isOk());
    }

    /**
     * Successful form login should land the user on /dashboard, not /.
     * Closes #142 — the home page is the unauthenticated marketing surface;
     * authenticated users belong on the app shell at /dashboard.
     */
    @Test
    void successfulFormLoginRedirectsToDashboard() throws Exception {
        mockMvc.perform(formLogin("/login").user("alice").password("secret"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));
    }

    /**
     * Confirms the security wall on /dashboard: an unauthenticated GET is
     * redirected to the login page. Pairs with the success-URL change so
     * we know the path we now redirect to is itself protected.
     */
    @Test
    void dashboardRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
    }
}
