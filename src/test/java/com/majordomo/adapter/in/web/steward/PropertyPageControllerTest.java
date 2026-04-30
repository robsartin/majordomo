package com.majordomo.adapter.in.web.steward;

import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.application.identity.CurrentOrganizationResolver;
import com.majordomo.application.identity.OrganizationAccessService;
import com.majordomo.application.steward.PropertyDetailView;
import com.majordomo.application.steward.PropertyDetailViewService;
import com.majordomo.domain.model.EntityNotFoundException;
import com.majordomo.domain.model.EntityType;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.model.steward.Property;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Slice tests for {@link PropertyPageController}'s detail handler.
 * Assembly logic is covered by {@code PropertyDetailViewServiceTest}.
 */
@WebMvcTest(PropertyPageController.class)
@Import(SecurityConfig.class)
class PropertyPageControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean ManagePropertyUseCase propertyUseCase;
    @MockitoBean PropertyRepository propertyRepository;
    @MockitoBean PropertyDetailViewService propertyDetailViewService;
    @MockitoBean CurrentOrganizationResolver currentOrg;
    @MockitoBean OrganizationAccessService organizationAccessService;
    @MockitoBean ApiKeyRepository apiKeyRepository;
    @MockitoBean OAuth2UserService oAuth2UserService;

    private static final UUID ORG_ID = UuidFactory.newId();

    @BeforeEach
    void seedAuth() {
        User user = new User(UuidFactory.newId(), "testuser", "test@example.com");
        when(currentOrg.resolve(any(UserDetails.class)))
                .thenReturn(new CurrentOrganizationResolver.Resolved(user, ORG_ID));
    }

    /** Detail handler delegates to the service and exposes its view fields. */
    @Test
    @WithMockUser(username = "testuser")
    void detailDelegatesToServiceAndExposesView() throws Exception {
        UUID propertyId = UuidFactory.newId();
        Property property = new Property();
        property.setId(propertyId);
        property.setName("Test Property");
        property.setOrganizationId(ORG_ID);
        PropertyDetailView view = new PropertyDetailView(property, null, List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        when(propertyDetailViewService.assemble(propertyId)).thenReturn(view);

        mockMvc.perform(get("/properties/{id}", propertyId))
                .andExpect(status().isOk())
                .andExpect(view().name("property-detail"))
                .andExpect(model().attributeExists("property"))
                .andExpect(model().attributeExists("username"))
                .andExpect(model().attributeExists("scheduleRows"))
                .andExpect(model().attributeExists("contactCandidates"))
                .andExpect(model().attributeExists("contactRoles"));
    }

    /** Unauthenticated access redirects to login. */
    @Test
    void detailRequiresAuthentication() throws Exception {
        UUID propertyId = UuidFactory.newId();
        mockMvc.perform(get("/properties/{id}", propertyId))
                .andExpect(status().is3xxRedirection());
    }

    /** When the service throws EntityNotFoundException the GlobalExceptionHandler maps to 404. */
    @Test
    @WithMockUser(username = "testuser")
    void detailReturns404WhenServiceMissing() throws Exception {
        UUID propertyId = UuidFactory.newId();
        when(propertyDetailViewService.assemble(propertyId))
                .thenThrow(new EntityNotFoundException(EntityType.PROPERTY.name(), propertyId));

        mockMvc.perform(get("/properties/{id}", propertyId))
                .andExpect(status().isNotFound());
    }
}
