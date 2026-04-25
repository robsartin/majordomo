package com.majordomo.adapter.in.web.config;

import com.majordomo.domain.model.EntityNotFoundException;
import com.majordomo.adapter.in.web.HomeController;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for {@link GlobalExceptionHandler}.
 */
@WebMvcTest(controllers = HomeController.class)
@Import({SecurityConfig.class, GlobalExceptionHandlerTest.ErrorTestController.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApiKeyRepository apiKeyRepository;

    @MockitoBean
    private OAuth2UserService oAuth2UserService;

    @RestController
    static class ErrorTestController {

        @GetMapping("/test/not-found")
        public void notFound() {
            throw new EntityNotFoundException("Property", UUID.randomUUID());
        }

        @GetMapping("/test/bad-request")
        public void badRequest() {
            throw new IllegalArgumentException("Invalid input");
        }

        @GetMapping("/test/server-error")
        public void serverError() {
            throw new RuntimeException("Something broke");
        }
    }

    /** Entity not found returns 404 with error body. */
    @Test
    @WithMockUser
    void entityNotFoundReturns404() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.path").value("/test/not-found"));
    }

    /** Bad request returns 400 with error body. */
    @Test
    @WithMockUser
    void illegalArgumentReturns400() throws Exception {
        mockMvc.perform(get("/test/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid input"));
    }

    /** Unhandled exception returns 500 with generic message. */
    @Test
    @WithMockUser
    void genericExceptionReturns500() throws Exception {
        mockMvc.perform(get("/test/server-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    /** Missing static resource returns 404, not 500. Regression for #127. */
    @Test
    @WithMockUser
    void missingResourceReturns404() throws Exception {
        // /favicon.ico exists now (#126); pick a path that genuinely 404s
        mockMvc.perform(get("/this-resource-does-not-exist.ico"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Resource not found"));
    }
}
