package com.majordomo.adapter.in.web.config;

import com.majordomo.adapter.in.web.HomeController;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.matchesRegex;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

/**
 * Tests for {@link CorrelationIdFilter}.
 */
@WebMvcTest(HomeController.class)
@Import({SecurityConfig.class, CorrelationIdFilter.class})
class CorrelationIdFilterTest {

    /** UUID regex pattern for validating generated correlation IDs. */
    private static final String UUID_REGEX =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApiKeyRepository apiKeyRepository;

    @MockitoBean
    private OAuth2UserService oAuth2UserService;

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
