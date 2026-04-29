package com.majordomo.adapter.in.web.concierge;

import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.application.identity.CurrentOrganizationResolver;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.concierge.Contact;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.port.in.concierge.ManageContactUseCase;
import com.majordomo.domain.port.out.concierge.ContactRepository;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** Slice tests for the {@code /contacts} list page (#231). */
@WebMvcTest(ContactPageController.class)
@Import(SecurityConfig.class)
class ContactPageListTest {

    @Autowired MockMvc mvc;

    @MockitoBean ManageContactUseCase contactUseCase;
    @MockitoBean ContactRepository contactRepository;
    @MockitoBean com.majordomo.domain.port.in.steward.ManagePropertyUseCase propertyUseCase;
    @MockitoBean com.majordomo.domain.port.out.steward.PropertyContactRepository propertyContactRepository;
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

    /** Cycle 1: GET /contacts returns 200 + contacts view, lists rows. */
    @Test
    @WithMockUser
    void listRendersContactsForOrg() throws Exception {
        Contact alice = contact("Alice Example", "Acme", "alice@acme.example", "+15555550100");
        Contact bob = contact("Bob Example", "Globex", "bob@globex.example", "+15555550200");
        when(contactRepository.findByOrganizationId(ORG_ID)).thenReturn(List.of(alice, bob));

        MvcResult result = mvc.perform(get("/contacts"))
                .andExpect(status().isOk())
                .andExpect(view().name("contacts"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body)
                .contains("Alice Example").contains("Acme").contains("alice@acme.example")
                .contains("Bob Example").contains("Globex").contains("bob@globex.example");
    }

    /** Cycle 2: q query narrows results across name + organization + emails. */
    @Test
    @WithMockUser
    void listFilteredByQ() throws Exception {
        Contact alice = contact("Alice Example", "Acme", "alice@acme.example", "+15555550100");
        Contact bob = contact("Bob Example", "Globex", "bob@globex.example", "+15555550200");
        when(contactRepository.findByOrganizationId(ORG_ID)).thenReturn(List.of(alice, bob));

        MvcResult result = mvc.perform(get("/contacts").param("q", "globex"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Bob Example").doesNotContain("Alice Example");
    }

    /** Cycle 2b: organization filter narrows to that organization's contacts only. */
    @Test
    @WithMockUser
    void listFilteredByOrganization() throws Exception {
        Contact alice = contact("Alice Example", "Acme", "alice@acme.example", "+15555550100");
        Contact bob = contact("Bob Example", "Globex", "bob@globex.example", "+15555550200");
        when(contactRepository.findByOrganizationId(ORG_ID)).thenReturn(List.of(alice, bob));

        MvcResult result = mvc.perform(get("/contacts").param("organization", "Acme"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Alice Example").doesNotContain("Bob Example");
    }

    /** Cycle 3: archived contacts are excluded. */
    @Test
    @WithMockUser
    void archivedContactsExcluded() throws Exception {
        Contact alice = contact("Alice Example", "Acme", "alice@acme.example", "+15555550100");
        Contact archived = contact("Old Contact", "Legacy", "old@legacy.example", "+15555550300");
        archived.setArchivedAt(Instant.now());
        when(contactRepository.findByOrganizationId(ORG_ID)).thenReturn(List.of(alice, archived));

        MvcResult result = mvc.perform(get("/contacts"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Alice Example").doesNotContain("Old Contact");
    }

    /** Cycle 3b: empty state when no contacts. */
    @Test
    @WithMockUser
    void emptyStateWhenNoContacts() throws Exception {
        when(contactRepository.findByOrganizationId(ORG_ID)).thenReturn(List.of());

        MvcResult result = mvc.perform(get("/contacts"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("No contacts");
    }

    private static Contact contact(String formattedName, String organization,
                                   String email, String phone) {
        Contact c = new Contact();
        c.setId(UuidFactory.newId());
        c.setOrganizationId(ORG_ID);
        c.setFormattedName(formattedName);
        c.setOrganization(organization);
        c.setEmails(List.of(email));
        c.setTelephones(List.of(phone));
        return c;
    }
}
