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
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for {@link SecurityConfig} verifying public and protected endpoint access.
 */
@WebMvcTest(controllers = {HomeController.class, LoginController.class, ContactController.class})
@Import(SecurityConfig.class)
class SecurityConfigTest {

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
}
