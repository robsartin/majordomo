package com.majordomo.adapter.in.web.config;

import com.majordomo.application.identity.CurrentOrganizationResolver;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** Slice tests for {@link OrgContextArgumentResolver} via a tiny test-only controller. */
@WebMvcTest(controllers = OrgContextProbeController.class)
@Import({SecurityConfig.class, WebConfig.class, OrgContextArgumentResolver.class,
        GlobalExceptionHandler.class})
class OrgContextArgumentResolverTest {

    @Autowired MockMvc mvc;

    @MockitoBean CurrentOrganizationResolver currentOrg;
    @MockitoBean ApiKeyRepository apiKeyRepository;
    @MockitoBean OAuth2UserService oAuth2UserService;

    private static final UUID ORG_ID = UuidFactory.newId();

    @BeforeEach
    void seedAuth() {
        User user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        when(currentOrg.resolve(any(UserDetails.class)))
                .thenReturn(new CurrentOrganizationResolver.Resolved(user, ORG_ID));
    }

    /** Cycle 1: handler taking OrgContext gets a populated record. */
    @Test
    @WithMockUser
    void handlerReceivesOrgContext() throws Exception {
        mvc.perform(get("/__test__/org-probe"))
                .andExpect(status().isOk())
                .andExpect(content().string("orgId=" + ORG_ID + ",user=robsartin"));
    }

    /** Cycle 2: when the resolver returns null orgId the handler short-circuits to redirect. */
    @Test
    @WithMockUser
    void handlerRedirectsWhenNoOrg() throws Exception {
        User user = new User(UuidFactory.newId(), "lonely", "lonely@example.com");
        when(currentOrg.resolve(any(UserDetails.class)))
                .thenReturn(new CurrentOrganizationResolver.Resolved(user, null));

        mvc.perform(get("/__test__/org-probe"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/"));
    }

}
