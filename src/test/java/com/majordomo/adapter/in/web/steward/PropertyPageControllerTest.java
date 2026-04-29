package com.majordomo.adapter.in.web.steward;

import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.domain.model.concierge.Contact;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.model.steward.PropertyContact;
import com.majordomo.domain.model.steward.PropertyStatus;
import com.majordomo.domain.port.in.ManageAttachmentUseCase;
import com.majordomo.domain.port.in.concierge.ManageContactUseCase;
import com.majordomo.domain.port.in.herald.ManageScheduleUseCase;
import com.majordomo.domain.port.in.steward.ManagePropertyUseCase;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;
import com.majordomo.domain.port.out.identity.MembershipRepository;
import com.majordomo.domain.port.out.identity.UserRepository;
import com.majordomo.domain.port.out.steward.PropertyContactRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Tests for {@link PropertyPageController}.
 */
@WebMvcTest(PropertyPageController.class)
@Import(SecurityConfig.class)
class PropertyPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ManagePropertyUseCase propertyUseCase;

    @MockitoBean
    private ManageScheduleUseCase scheduleUseCase;

    @MockitoBean
    private ManageContactUseCase contactUseCase;

    @MockitoBean
    private ManageAttachmentUseCase attachmentUseCase;

    @MockitoBean
    private PropertyContactRepository propertyContactRepository;

    @MockitoBean
    private com.majordomo.domain.port.out.steward.PropertyRepository propertyRepository;

    @MockitoBean
    private com.majordomo.application.identity.CurrentOrganizationResolver currentOrg;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private MembershipRepository membershipRepository;

    @MockitoBean
    private ApiKeyRepository apiKeyRepository;

    @MockitoBean
    private OAuth2UserService oAuth2UserService;

    /** Authenticated user requesting an existing property receives 200 and the detail view. */
    @Test
    @WithMockUser(username = "testuser")
    void detailReturns200ForAuthenticatedUser() throws Exception {
        UUID propertyId = UUID.randomUUID();

        Property property = new Property();
        property.setId(propertyId);
        property.setName("Test Property");
        property.setStatus(PropertyStatus.ACTIVE);

        User user = new User(UUID.randomUUID(), "testuser", "test@example.com");

        when(propertyUseCase.findById(propertyId)).thenReturn(Optional.of(property));
        when(propertyUseCase.findByParentId(propertyId)).thenReturn(List.of());
        when(propertyContactRepository.findByPropertyId(propertyId)).thenReturn(List.of());
        when(scheduleUseCase.findByPropertyId(propertyId)).thenReturn(List.of());
        when(attachmentUseCase.list("property", propertyId)).thenReturn(List.of());
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        mockMvc.perform(get("/properties/{id}", propertyId))
                .andExpect(status().isOk())
                .andExpect(view().name("property-detail"))
                .andExpect(model().attributeExists("property"))
                .andExpect(model().attributeExists("username"));
    }

    /** Unauthenticated access to the property detail page redirects to login. */
    @Test
    void detailRequiresAuthentication() throws Exception {
        UUID propertyId = UUID.randomUUID();

        mockMvc.perform(get("/properties/{id}", propertyId))
                .andExpect(status().is3xxRedirection());
    }

    /** Requesting a non-existent property redirects to the dashboard. */
    @Test
    @WithMockUser(username = "testuser")
    void detailRedirectsWhenPropertyNotFound() throws Exception {
        UUID propertyId = UUID.randomUUID();

        when(propertyUseCase.findById(propertyId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/properties/{id}", propertyId))
                .andExpect(status().is3xxRedirection());
    }

    /** Property with a parent loads the parent and adds it to the model. */
    @Test
    @WithMockUser(username = "testuser")
    void detailLoadsParentProperty() throws Exception {
        UUID parentId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();

        Property parent = new Property();
        parent.setId(parentId);
        parent.setName("Parent Property");

        Property property = new Property();
        property.setId(propertyId);
        property.setName("Child Property");
        property.setParentId(parentId);

        User user = new User(UUID.randomUUID(), "testuser", "test@example.com");

        when(propertyUseCase.findById(propertyId)).thenReturn(Optional.of(property));
        when(propertyUseCase.findById(parentId)).thenReturn(Optional.of(parent));
        when(propertyUseCase.findByParentId(propertyId)).thenReturn(List.of());
        when(propertyContactRepository.findByPropertyId(propertyId)).thenReturn(List.of());
        when(scheduleUseCase.findByPropertyId(propertyId)).thenReturn(List.of());
        when(attachmentUseCase.list("property", propertyId)).thenReturn(List.of());
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        mockMvc.perform(get("/properties/{id}", propertyId))
                .andExpect(status().isOk())
                .andExpect(view().name("property-detail"))
                .andExpect(model().attributeExists("parent"));
    }

    /** Property with linked contacts adds contacts and propertyContacts to the model. */
    @Test
    @WithMockUser(username = "testuser")
    void detailLoadsLinkedContacts() throws Exception {
        UUID propertyId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();

        Property property = new Property();
        property.setId(propertyId);
        property.setName("Test Property");

        PropertyContact pc = new PropertyContact();
        pc.setId(UUID.randomUUID());
        pc.setPropertyId(propertyId);
        pc.setContactId(contactId);

        Contact contact = new Contact();
        contact.setId(contactId);
        contact.setFormattedName("Acme Plumbing");

        User user = new User(UUID.randomUUID(), "testuser", "test@example.com");

        when(propertyUseCase.findById(propertyId)).thenReturn(Optional.of(property));
        when(propertyUseCase.findByParentId(propertyId)).thenReturn(List.of());
        when(propertyContactRepository.findByPropertyId(propertyId)).thenReturn(List.of(pc));
        when(contactUseCase.findById(contactId)).thenReturn(Optional.of(contact));
        when(scheduleUseCase.findByPropertyId(propertyId)).thenReturn(List.of());
        when(attachmentUseCase.list("property", propertyId)).thenReturn(List.of());
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        mockMvc.perform(get("/properties/{id}", propertyId))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("contacts"))
                .andExpect(model().attributeExists("propertyContacts"));
    }
}
