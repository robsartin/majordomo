package com.majordomo.adapter.in.web.config;

import com.majordomo.domain.model.EntityNotFoundException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.test.context.support.WithMockUser;
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
@SpringBootTest
@AutoConfigureMockMvc
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @TestConfiguration
    static class TestControllerConfig {

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
}
