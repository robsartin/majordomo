package com.majordomo.adapter.in.web.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.matchesRegex;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for {@link CorrelationIdFilter}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CorrelationIdFilterTest {

    /** UUID regex pattern for validating generated correlation IDs. */
    private static final String UUID_REGEX =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Autowired
    private MockMvc mockMvc;

    /** Request without header gets a generated correlation ID in the response. */
    @Test
    @WithMockUser
    void requestWithoutHeaderGetsGeneratedCorrelationId() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(header().string("X-Correlation-ID", matchesRegex(UUID_REGEX)));
    }

    /** Request with header echoes the same correlation ID back. */
    @Test
    @WithMockUser
    void requestWithHeaderEchoesCorrelationId() throws Exception {
        String customId = "my-custom-correlation-id";
        mockMvc.perform(get("/").header("X-Correlation-ID", customId))
                .andExpect(header().string("X-Correlation-ID", customId));
    }

    /** Correlation ID is present in response even for non-existent endpoints. */
    @Test
    @WithMockUser
    void nonExistentEndpointStillHasCorrelationId() throws Exception {
        mockMvc.perform(get("/api/nonexistent"))
                .andExpect(header().string("X-Correlation-ID", matchesRegex(UUID_REGEX)));
    }
}
