package com.majordomo.adapter.in.web;

import com.majordomo.adapter.in.web.concierge.ContactController;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.herald.ScheduleController;
import com.majordomo.adapter.in.web.steward.PropertyController;
import com.majordomo.application.identity.OrganizationAccessService;
import com.majordomo.domain.port.in.concierge.ManageContactUseCase;
import com.majordomo.domain.port.in.herald.ManageScheduleUseCase;
import com.majordomo.domain.port.in.steward.ManagePropertyUseCase;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests verifying input validation returns 400 with field-level errors.
 */
@WebMvcTest(controllers = {ContactController.class, PropertyController.class, ScheduleController.class})
@Import(SecurityConfig.class)
class ValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ManageContactUseCase contactUseCase;

    @MockitoBean
    private ManagePropertyUseCase propertyUseCase;

    @MockitoBean
    private ManageScheduleUseCase scheduleUseCase;

    @MockitoBean
    private com.majordomo.application.herald.ScheduleAccessGuard scheduleAccessGuard;

    @MockitoBean
    private OrganizationAccessService organizationAccessService;

    @MockitoBean
    private ApiKeyRepository apiKeyRepository;

    @MockitoBean
    private OAuth2UserService oAuth2UserService;

    /** Contact with blank formattedName should return 400. */
    @Test
    @WithMockUser
    void createContactBlankNameReturns400() throws Exception {
        mockMvc.perform(post("/api/contacts")
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
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"propertyId\":\"019606a0-0000-7000-8000-000000000001\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }
}
