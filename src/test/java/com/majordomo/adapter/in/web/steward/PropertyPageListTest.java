package com.majordomo.adapter.in.web.steward;

import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.application.identity.CurrentOrganizationResolver;
import com.majordomo.application.steward.PropertyFilters;
import com.majordomo.application.steward.PropertyQueryService;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.model.steward.PropertyStatus;
import com.majordomo.domain.port.in.steward.ManagePropertyUseCase;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;
import com.majordomo.domain.port.out.steward.PropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Slice tests for {@link PropertyPageController#list}. Filter/sort behavior
 * itself is covered by {@link com.majordomo.application.steward.PropertyQueryServiceTest}.
 */
@WebMvcTest(PropertyPageController.class)
@Import(SecurityConfig.class)
class PropertyPageListTest {

    @Autowired MockMvc mvc;

    @MockitoBean ManagePropertyUseCase propertyUseCase;
    @MockitoBean PropertyRepository propertyRepository;
    @MockitoBean PropertyQueryService propertyQueryService;
    @MockitoBean com.majordomo.application.steward.PropertyDetailViewService propertyDetailViewService;
    @MockitoBean CurrentOrganizationResolver currentOrg;
    @MockitoBean com.majordomo.application.identity.OrganizationAccessService organizationAccessService;
    @MockitoBean ApiKeyRepository apiKeyRepository;
    @MockitoBean OAuth2UserService oAuth2UserService;

    private static final UUID ORG_ID = UuidFactory.newId();

    @BeforeEach
    void seedAuth() {
        User user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        when(currentOrg.resolve(any(UserDetails.class)))
                .thenReturn(new CurrentOrganizationResolver.Resolved(user, ORG_ID));
    }

    /** Controller delegates to the service and renders its rows. */
    @Test
    @WithMockUser
    void listDelegatesToServiceAndRendersRows() throws Exception {
        Property apartment = property("Apartment");
        Property beach = property("Beach House");
        when(propertyQueryService.list(eq(ORG_ID), any(PropertyFilters.class)))
                .thenReturn(List.of(apartment, beach));

        var result = mvc.perform(get("/properties"))
                .andExpect(status().isOk())
                .andExpect(view().name("properties"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .contains("Apartment").contains("Beach House");
    }

    /** Filter parameters propagate to the service call. */
    @Test
    @WithMockUser
    void listForwardsFiltersToService() throws Exception {
        when(propertyQueryService.list(eq(ORG_ID), any(PropertyFilters.class)))
                .thenReturn(List.of());

        mvc.perform(get("/properties").param("category", "rental").param("q", "apt"))
                .andExpect(status().isOk());

        verify(propertyQueryService).list(eq(ORG_ID), eq(new PropertyFilters("rental", "apt")));
    }

    /** Empty state renders when service returns no rows. */
    @Test
    @WithMockUser
    void emptyStateRendersWhenNoRows() throws Exception {
        when(propertyQueryService.list(eq(ORG_ID), any(PropertyFilters.class)))
                .thenReturn(List.of());

        mvc.perform(get("/properties"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "No properties match")));
    }

    /** User without an organization redirects home. */
    @Test
    @WithMockUser
    void redirectsHomeWhenNoOrganization() throws Exception {
        User lonely = new User(UuidFactory.newId(), "lonely", "l@example.com");
        when(currentOrg.resolve(any(UserDetails.class)))
                .thenReturn(new CurrentOrganizationResolver.Resolved(lonely, null));

        mvc.perform(get("/properties"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/"));
    }

    /** Unauthenticated request redirects to login. */
    @Test
    void unauthenticatedRedirectsToLogin() throws Exception {
        mvc.perform(get("/properties"))
                .andExpect(status().is3xxRedirection());
    }

    private static Property property(String name) {
        Property p = new Property();
        p.setId(UuidFactory.newId());
        p.setOrganizationId(ORG_ID);
        p.setName(name);
        p.setStatus(PropertyStatus.ACTIVE);
        return p;
    }
}
