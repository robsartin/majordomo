package com.majordomo.adapter.in.web.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for {@link SecurityConfig} verifying public and protected endpoint access.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

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
}
