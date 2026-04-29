package com.majordomo.adapter.in.web.steward;

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

/** Slice tests for property→contact link/unlink (#240). */
@WebMvcTest(PropertyContactLinkController.class)
@Import(SecurityConfig.class)
class PropertyPageLinkContactTest {

    @Autowired MockMvc mvc;

    @MockitoBean ManagePropertyUseCase propertyUseCase;
    @MockitoBean ContactRepository contactRepository;
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

    /** POST /properties/{id}/contacts persists a new PropertyContact and redirects. */
    @Test
    @WithMockUser
    void linkContactPersistsAndRedirects() throws Exception {
        UUID propId = UuidFactory.newId();
        UUID contactId = UuidFactory.newId();
        Property property = property(propId);
        when(propertyUseCase.findById(propId)).thenReturn(Optional.of(property));
        when(contactRepository.findById(contactId))
                .thenReturn(Optional.of(contact(contactId, "Carol")));
        when(propertyContactRepository.save(any(PropertyContact.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(post("/properties/{id}/contacts", propId)
                        .with(csrf())
                        .param("contactId", contactId.toString())
                        .param("role", "VENDOR")
                        .param("notes", "HVAC tech"))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<PropertyContact> captor = ArgumentCaptor.forClass(PropertyContact.class);
        verify(propertyContactRepository).save(captor.capture());
        PropertyContact saved = captor.getValue();
        assertThat(saved.getPropertyId()).isEqualTo(propId);
        assertThat(saved.getContactId()).isEqualTo(contactId);
        assertThat(saved.getRole().name()).isEqualTo("VENDOR");
        assertThat(saved.getNotes()).isEqualTo("HVAC tech");
    }

    /** POST /properties/{id}/contacts/{linkId}/unlink soft-deletes (sets archivedAt). */
    @Test
    @WithMockUser
    void unlinkContactSoftDeletesLink() throws Exception {
        UUID propId = UuidFactory.newId();
        UUID contactId = UuidFactory.newId();
        UUID linkId = UuidFactory.newId();
        Property property = property(propId);
        PropertyContact link = propertyContactLink(propId, contactId);
        link.setId(linkId);
        when(propertyUseCase.findById(propId)).thenReturn(Optional.of(property));
        when(propertyContactRepository.findById(linkId)).thenReturn(Optional.of(link));
        when(propertyContactRepository.save(any(PropertyContact.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(post("/properties/{id}/contacts/{linkId}/unlink", propId, linkId)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<PropertyContact> captor = ArgumentCaptor.forClass(PropertyContact.class);
        verify(propertyContactRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(linkId);
        assertThat(captor.getValue().getArchivedAt()).isNotNull();
    }

    /** Cross-org link returns 403 (verified against the property's org). */
    @Test
    @WithMockUser
    void linkContactReturns403WhenCrossOrgProperty() throws Exception {
        UUID propId = UuidFactory.newId();
        UUID otherOrg = UuidFactory.newId();
        Property foreign = property(propId);
        foreign.setOrganizationId(otherOrg);
        when(propertyUseCase.findById(propId)).thenReturn(Optional.of(foreign));
        doThrow(new AccessDeniedException("denied"))
                .when(organizationAccessService).verifyAccess(otherOrg);

        mvc.perform(post("/properties/{id}/contacts", propId)
                        .with(csrf())
                        .param("contactId", UuidFactory.newId().toString())
                        .param("role", "VENDOR"))
                .andExpect(status().isForbidden());

        verify(propertyContactRepository, never()).save(any());
    }

    /** Cross-org unlink returns 403. */
    @Test
    @WithMockUser
    void unlinkContactReturns403WhenCrossOrgProperty() throws Exception {
        UUID propId = UuidFactory.newId();
        UUID otherOrg = UuidFactory.newId();
        UUID linkId = UuidFactory.newId();
        Property foreign = property(propId);
        foreign.setOrganizationId(otherOrg);
        when(propertyUseCase.findById(propId)).thenReturn(Optional.of(foreign));
        doThrow(new AccessDeniedException("denied"))
                .when(organizationAccessService).verifyAccess(otherOrg);

        mvc.perform(post("/properties/{id}/contacts/{linkId}/unlink", propId, linkId)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(propertyContactRepository, never()).save(any());
    }

    private static Property property(UUID id) {
        Property p = new Property();
        p.setId(id);
        p.setOrganizationId(ORG_ID);
        p.setName("Property " + id);
        return p;
    }

    private static Contact contact(UUID id, String name) {
        Contact c = new Contact();
        c.setId(id);
        c.setOrganizationId(ORG_ID);
        c.setFormattedName(name);
        return c;
    }

    private static PropertyContact propertyContactLink(UUID propId, UUID contactId) {
        PropertyContact pc = new PropertyContact();
        pc.setId(UuidFactory.newId());
        pc.setPropertyId(propId);
        pc.setContactId(contactId);
        pc.setRole(com.majordomo.domain.model.concierge.ContactRole.VENDOR);
        return pc;
    }
}
