package com.majordomo.adapter.in.web.concierge;

import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.application.identity.CurrentOrganizationResolver;
import com.majordomo.application.identity.OrganizationAccessService;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.concierge.Contact;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.model.steward.PropertyContact;
import com.majordomo.domain.port.in.steward.ManagePropertyUseCase;
import com.majordomo.domain.port.out.concierge.ContactRepository;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;
import com.majordomo.domain.port.out.steward.PropertyContactRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Slice tests for contact→property link/unlink (#240). */
@WebMvcTest(ContactPropertyLinkController.class)
@Import(SecurityConfig.class)
class ContactPageLinkPropertyTest {

    @Autowired MockMvc mvc;

    @MockitoBean ContactRepository contactRepository;
    @MockitoBean ManagePropertyUseCase propertyUseCase;
    @MockitoBean PropertyContactRepository propertyContactRepository;
    @MockitoBean CurrentOrganizationResolver currentOrg;
    @MockitoBean OrganizationAccessService organizationAccessService;
    @MockitoBean ApiKeyRepository apiKeyRepository;
    @MockitoBean OAuth2UserService oAuth2UserService;

    private static final UUID ORG_ID = UuidFactory.newId();

    @BeforeEach
    void seedAuth() {
        User user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        when(currentOrg.resolve(any(UserDetails.class)))
                .thenReturn(new CurrentOrganizationResolver.Resolved(user, ORG_ID));
    }

    /** POST /contacts/{id}/properties persists a new PropertyContact. */
    @Test
    @WithMockUser
    void linkPropertyPersistsAndRedirects() throws Exception {
        UUID contactId = UuidFactory.newId();
        UUID propId = UuidFactory.newId();
        when(contactRepository.findById(contactId)).thenReturn(Optional.of(contact(contactId)));
        when(propertyUseCase.findById(propId)).thenReturn(Optional.of(property(propId)));
        when(propertyContactRepository.save(any(PropertyContact.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(post("/contacts/{id}/properties", contactId)
                        .with(csrf())
                        .param("propertyId", propId.toString())
                        .param("role", "MANUFACTURER"))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<PropertyContact> captor = ArgumentCaptor.forClass(PropertyContact.class);
        verify(propertyContactRepository).save(captor.capture());
        assertThat(captor.getValue().getContactId()).isEqualTo(contactId);
        assertThat(captor.getValue().getPropertyId()).isEqualTo(propId);
        assertThat(captor.getValue().getRole().name()).isEqualTo("MANUFACTURER");
    }

    /** POST /contacts/{id}/properties/{linkId}/unlink soft-deletes. */
    @Test
    @WithMockUser
    void unlinkPropertySoftDeletesLink() throws Exception {
        UUID contactId = UuidFactory.newId();
        UUID propId = UuidFactory.newId();
        UUID linkId = UuidFactory.newId();
        Contact contact = contact(contactId);
        PropertyContact link = new PropertyContact();
        link.setId(linkId);
        link.setContactId(contactId);
        link.setPropertyId(propId);
        when(contactRepository.findById(contactId)).thenReturn(Optional.of(contact));
        when(propertyContactRepository.findById(linkId)).thenReturn(Optional.of(link));
        when(propertyContactRepository.save(any(PropertyContact.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(post("/contacts/{id}/properties/{linkId}/unlink", contactId, linkId)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<PropertyContact> captor = ArgumentCaptor.forClass(PropertyContact.class);
        verify(propertyContactRepository).save(captor.capture());
        assertThat(captor.getValue().getArchivedAt()).isNotNull();
    }

    /** Cross-org link returns 403. */
    @Test
    @WithMockUser
    void linkPropertyReturns403WhenCrossOrgContact() throws Exception {
        UUID contactId = UuidFactory.newId();
        UUID otherOrg = UuidFactory.newId();
        Contact foreign = contact(contactId);
        foreign.setOrganizationId(otherOrg);
        when(contactRepository.findById(contactId)).thenReturn(Optional.of(foreign));
        doThrow(new AccessDeniedException("denied"))
                .when(organizationAccessService).verifyAccess(otherOrg);

        mvc.perform(post("/contacts/{id}/properties", contactId)
                        .with(csrf())
                        .param("propertyId", UuidFactory.newId().toString())
                        .param("role", "VENDOR"))
                .andExpect(status().isForbidden());

        verify(propertyContactRepository, never()).save(any());
    }

    /** Cross-org unlink returns 403. */
    @Test
    @WithMockUser
    void unlinkPropertyReturns403WhenCrossOrgContact() throws Exception {
        UUID contactId = UuidFactory.newId();
        UUID otherOrg = UuidFactory.newId();
        UUID linkId = UuidFactory.newId();
        Contact foreign = contact(contactId);
        foreign.setOrganizationId(otherOrg);
        when(contactRepository.findById(contactId)).thenReturn(Optional.of(foreign));
        doThrow(new AccessDeniedException("denied"))
                .when(organizationAccessService).verifyAccess(otherOrg);

        mvc.perform(post("/contacts/{id}/properties/{linkId}/unlink", contactId, linkId)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(propertyContactRepository, never()).save(any());
    }

    private static Contact contact(UUID id) {
        Contact c = new Contact();
        c.setId(id);
        c.setOrganizationId(ORG_ID);
        c.setFormattedName("Carol");
        return c;
    }

    private static Property property(UUID id) {
        Property p = new Property();
        p.setId(id);
        p.setOrganizationId(ORG_ID);
        p.setName("Beach House");
        return p;
    }
}
