package com.majordomo.adapter.in.web.concierge;

import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.application.identity.CurrentOrganizationResolver;
import com.majordomo.application.identity.OrganizationAccessService;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.concierge.Address;
import com.majordomo.domain.model.concierge.Contact;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.model.steward.PropertyContact;
import com.majordomo.domain.port.in.concierge.ManageContactUseCase;
import com.majordomo.domain.port.in.steward.ManagePropertyUseCase;
import com.majordomo.domain.port.out.concierge.ContactRepository;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;
import com.majordomo.domain.port.out.steward.PropertyContactRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** Slice tests for the {@code /contacts/{id}} detail page (#232). */
@WebMvcTest(ContactPageController.class)
@Import(SecurityConfig.class)
class ContactPageDetailTest {

    @Autowired MockMvc mvc;

    @MockitoBean ManageContactUseCase contactUseCase;
    @MockitoBean ContactRepository contactRepository;
    @MockitoBean ManagePropertyUseCase propertyUseCase;
    @MockitoBean com.majordomo.domain.port.out.steward.PropertyRepository propertyRepository;
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

    /** Cycle 1: GET /contacts/{id} renders header + panels. */
    @Test
    @WithMockUser
    void detailRendersContactWithPanels() throws Exception {
        UUID id = UuidFactory.newId();
        Contact c = new Contact();
        c.setId(id);
        c.setOrganizationId(ORG_ID);
        c.setFormattedName("Alice Example");
        c.setOrganization("Acme Corp");
        c.setTitle("Lead Engineer");
        c.setEmails(List.of("alice@acme.example", "alice.alt@acme.example"));
        c.setTelephones(List.of("+15555550100"));
        c.setUrls(List.of("https://acme.example"));
        c.setNicknames(List.of("Ace"));
        c.setNotes("Met at conference 2025.");
        c.setAddresses(List.of(new Address(
                UuidFactory.newId(), id, "WORK",
                "100 Main St", "Springfield", "IL", "62701", "USA")));
        when(contactRepository.findById(id)).thenReturn(Optional.of(c));
        when(propertyContactRepository.findByContactId(id)).thenReturn(List.of());

        MvcResult result = mvc.perform(get("/contacts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(view().name("contact-detail"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body)
                .contains("Alice Example")
                .contains("Acme Corp")
                .contains("Lead Engineer")
                .contains("alice@acme.example")
                .contains("alice.alt@acme.example")
                .contains("+15555550100")
                .contains("https://acme.example")
                .contains("Ace")
                .contains("Met at conference 2025.")
                .contains("100 Main St")
                .contains("Springfield");
    }

    /** Cycle 2: 404 when contact missing. */
    @Test
    @WithMockUser
    void detailReturns404WhenMissing() throws Exception {
        UUID id = UuidFactory.newId();
        when(contactRepository.findById(id)).thenReturn(Optional.empty());

        mvc.perform(get("/contacts/{id}", id))
                .andExpect(status().isNotFound());
    }

    /** Cycle 2b: 404 when contact archived. */
    @Test
    @WithMockUser
    void detailReturns404WhenArchived() throws Exception {
        UUID id = UuidFactory.newId();
        Contact archived = new Contact();
        archived.setId(id);
        archived.setOrganizationId(ORG_ID);
        archived.setFormattedName("Old");
        archived.setArchivedAt(Instant.now());
        when(contactRepository.findById(id)).thenReturn(Optional.of(archived));

        mvc.perform(get("/contacts/{id}", id))
                .andExpect(status().isNotFound());
    }

    /** Cycle 3: 403 when contact belongs to another org. */
    @Test
    @WithMockUser
    void detailReturns403WhenCrossOrg() throws Exception {
        UUID id = UuidFactory.newId();
        UUID otherOrg = UuidFactory.newId();
        Contact foreign = new Contact();
        foreign.setId(id);
        foreign.setOrganizationId(otherOrg);
        foreign.setFormattedName("Cross-org");
        when(contactRepository.findById(id)).thenReturn(Optional.of(foreign));
        doThrow(new AccessDeniedException("denied"))
                .when(organizationAccessService).verifyAccess(otherOrg);

        mvc.perform(get("/contacts/{id}", id))
                .andExpect(status().isForbidden());
    }

    /** Cycle 4: linked properties panel renders associated properties. */
    @Test
    @WithMockUser
    void detailLinkedPropertiesPanel() throws Exception {
        UUID id = UuidFactory.newId();
        UUID propA = UuidFactory.newId();
        UUID propB = UuidFactory.newId();
        Contact c = new Contact();
        c.setId(id);
        c.setOrganizationId(ORG_ID);
        c.setFormattedName("Linked Larry");
        when(contactRepository.findById(id)).thenReturn(Optional.of(c));
        when(propertyContactRepository.findByContactId(id)).thenReturn(List.of(
                propertyContact(id, propA),
                propertyContact(id, propB)));
        Property a = new Property();
        a.setId(propA);
        a.setOrganizationId(ORG_ID);
        a.setName("Beach House");
        Property b = new Property();
        b.setId(propB);
        b.setOrganizationId(ORG_ID);
        b.setName("Mountain Cabin");
        when(propertyUseCase.findById(propA)).thenReturn(Optional.of(a));
        when(propertyUseCase.findById(propB)).thenReturn(Optional.of(b));

        MvcResult result = mvc.perform(get("/contacts/{id}", id))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Beach House").contains("Mountain Cabin");
        assertThat(body).contains("/properties/" + propA).contains("/properties/" + propB);
    }

    private static PropertyContact propertyContact(UUID contactId, UUID propertyId) {
        PropertyContact pc = new PropertyContact();
        pc.setId(UuidFactory.newId());
        pc.setContactId(contactId);
        pc.setPropertyId(propertyId);
        return pc;
    }
}
