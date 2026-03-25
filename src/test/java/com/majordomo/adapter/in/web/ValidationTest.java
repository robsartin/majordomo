package com.majordomo.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests verifying input validation returns 400 with field-level errors.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ValidationTest {

    @Autowired
    private MockMvc mockMvc;

    /** Contact with blank formattedName should return 400. */
    @Test
    @WithMockUser
    void createContactBlankNameReturns400() throws Exception {
        mockMvc.perform(post("/api/contacts")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"formattedName\":\"\","
                        + "\"organizationId\":\"019606a0-0000-7000-8000-000000000003\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    /** Property with blank name should return 400. */
    @Test
    @WithMockUser
    void createPropertyBlankNameReturns400() throws Exception {
        mockMvc.perform(post("/api/properties")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\","
                        + "\"organizationId\":\"019606a0-0000-7000-8000-000000000003\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    /** Schedule with missing required fields should return 400. */
    @Test
    @WithMockUser
    void createScheduleMissingFieldsReturns400() throws Exception {
        mockMvc.perform(post("/api/schedules")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"propertyId\":\"019606a0-0000-7000-8000-000000000001\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }
}
