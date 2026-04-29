package com.majordomo.adapter.in.web.concierge;

import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.application.identity.CurrentOrganizationResolver;
import com.majordomo.application.identity.OrganizationAccessService;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.concierge.Contact;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.port.in.concierge.ManageContactUseCase;
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
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** Slice tests for the {@code /contacts} add/edit forms (#233). */
@WebMvcTest(ContactPageController.class)
@Import(SecurityConfig.class)
class ContactPageFormTest {

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

    /** Cycle 1: GET /contacts/new returns 200 + contact-form view. */
    @Test
    @WithMockUser
    void newFormRenders() throws Exception {
        mvc.perform(get("/contacts/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("contact-form"));
    }

    /** Cycle 1: POST /contacts persists all simple + multi-value fields. */
    @Test
    @WithMockUser
    void createPersistsAndRedirectsToDetail() throws Exception {
        UUID newId = UuidFactory.newId();
        Contact saved = new Contact();
        saved.setId(newId);
        saved.setOrganizationId(ORG_ID);
        when(contactUseCase.create(any(Contact.class))).thenReturn(saved);

        mvc.perform(post("/contacts")
                        .with(csrf())
                        .param("formattedName", "Alice Example")
                        .param("givenName", "Alice")
                        .param("familyName", "Example")
                        .param("organization", "Acme")
                        .param("title", "Lead Engineer")
                        .param("notes", "Met at conference.")
                        .param("emails", "alice@acme.example\nalice.alt@acme.example")
                        .param("telephones", "+15555550100")
                        .param("urls", "https://acme.example")
                        .param("nicknames", "Ace"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/contacts/" + newId));

        ArgumentCaptor<Contact> captor = ArgumentCaptor.forClass(Contact.class);
        verify(contactUseCase).create(captor.capture());
        Contact c = captor.getValue();
        assertThat(c.getOrganizationId()).isEqualTo(ORG_ID);
        assertThat(c.getFormattedName()).isEqualTo("Alice Example");
        assertThat(c.getGivenName()).isEqualTo("Alice");
        assertThat(c.getFamilyName()).isEqualTo("Example");
        assertThat(c.getOrganization()).isEqualTo("Acme");
        assertThat(c.getTitle()).isEqualTo("Lead Engineer");
        assertThat(c.getNotes()).isEqualTo("Met at conference.");
        assertThat(c.getEmails()).containsExactly("alice@acme.example", "alice.alt@acme.example");
        assertThat(c.getTelephones()).containsExactly("+15555550100");
        assertThat(c.getUrls()).containsExactly("https://acme.example");
        assertThat(c.getNicknames()).containsExactly("Ace");
    }

    /** Cycle 2: POST /contacts with blank formattedName re-renders with error + state. */
    @Test
    @WithMockUser
    void createWithBlankNameRendersFormWithError() throws Exception {
        MvcResult result = mvc.perform(post("/contacts")
                        .with(csrf())
                        .param("formattedName", "")
                        .param("organization", "Acme")
                        .param("emails", "alice@acme.example"))
                .andExpect(status().isOk())
                .andExpect(view().name("contact-form"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Formatted name is required.");
        assertThat(body).contains("value=\"Acme\"");
        // Multi-line emails echo back as the textarea content.
        assertThat(body).contains("alice@acme.example");
        verify(contactUseCase, never()).create(any());
    }

    /** Cycle 3: GET /contacts/{id}/edit pre-populates the form. */
    @Test
    @WithMockUser
    void editFormPrePopulates() throws Exception {
        UUID id = UuidFactory.newId();
        Contact existing = new Contact();
        existing.setId(id);
        existing.setOrganizationId(ORG_ID);
        existing.setFormattedName("Alice Example");
        existing.setOrganization("Acme");
        existing.setEmails(List.of("alice@acme.example", "alice.alt@acme.example"));
        existing.setTelephones(List.of("+15555550100"));
        when(contactRepository.findById(id)).thenReturn(Optional.of(existing));

        MvcResult result = mvc.perform(get("/contacts/{id}/edit", id))
                .andExpect(status().isOk())
                .andExpect(view().name("contact-form"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body)
                .contains("Edit contact")
                .contains("value=\"Alice Example\"")
                .contains("value=\"Acme\"")
                .contains("alice@acme.example")
                .contains("alice.alt@acme.example")
                .contains("+15555550100");
    }

    /** Cycle 3: POST /contacts/{id} persists all fields and redirects. */
    @Test
    @WithMockUser
    void updatePersistsAndRedirectsToDetail() throws Exception {
        UUID id = UuidFactory.newId();
        Contact existing = new Contact();
        existing.setId(id);
        existing.setOrganizationId(ORG_ID);
        existing.setFormattedName("Old Name");
        when(contactRepository.findById(id)).thenReturn(Optional.of(existing));
        when(contactUseCase.update(eq(id), any(Contact.class))).thenAnswer(inv -> inv.getArgument(1));

        mvc.perform(post("/contacts/{id}", id)
                        .with(csrf())
                        .param("formattedName", "New Name")
                        .param("givenName", "New")
                        .param("familyName", "Name")
                        .param("organization", "Globex")
                        .param("title", "VP")
                        .param("emails", "new@globex.example")
                        .param("telephones", "+15555550999")
                        .param("urls", "https://globex.example")
                        .param("nicknames", "Newt"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/contacts/" + id));

        ArgumentCaptor<Contact> captor = ArgumentCaptor.forClass(Contact.class);
        verify(contactUseCase).update(eq(id), captor.capture());
        Contact c = captor.getValue();
        assertThat(c.getFormattedName()).isEqualTo("New Name");
        assertThat(c.getOrganization()).isEqualTo("Globex");
        assertThat(c.getEmails()).containsExactly("new@globex.example");
        assertThat(c.getNicknames()).containsExactly("Newt");
    }

    /** Cycle 4: bad email line on create re-renders form with error + state. */
    @Test
    @WithMockUser
    void createWithBadEmailRendersFormWithError() throws Exception {
        mvc.perform(post("/contacts")
                        .with(csrf())
                        .param("formattedName", "Alice Example")
                        .param("emails", "alice@acme.example\nnot-an-email"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Invalid email: not-an-email")));

        verify(contactUseCase, never()).create(any());
    }

    /** Cycle 5: GET /contacts/{id}/edit returns 403 cross-org. */
    @Test
    @WithMockUser
    void editFormReturns403WhenCrossOrg() throws Exception {
        UUID id = UuidFactory.newId();
        UUID otherOrg = UuidFactory.newId();
        Contact foreign = new Contact();
        foreign.setId(id);
        foreign.setOrganizationId(otherOrg);
        foreign.setFormattedName("Cross-org");
        when(contactRepository.findById(id)).thenReturn(Optional.of(foreign));
        doThrow(new AccessDeniedException("denied"))
                .when(organizationAccessService).verifyAccess(otherOrg);

        mvc.perform(get("/contacts/{id}/edit", id))
                .andExpect(status().isForbidden());
    }

    /** Cycle 5: POST /contacts/{id} returns 403 cross-org. */
    @Test
    @WithMockUser
    void updateReturns403WhenCrossOrg() throws Exception {
        UUID id = UuidFactory.newId();
        UUID otherOrg = UuidFactory.newId();
        Contact foreign = new Contact();
        foreign.setId(id);
        foreign.setOrganizationId(otherOrg);
        foreign.setFormattedName("Cross-org");
        when(contactRepository.findById(id)).thenReturn(Optional.of(foreign));
        doThrow(new AccessDeniedException("denied"))
                .when(organizationAccessService).verifyAccess(otherOrg);

        mvc.perform(post("/contacts/{id}", id)
                        .with(csrf())
                        .param("formattedName", "anything"))
                .andExpect(status().isForbidden());

        verify(contactUseCase, never()).update(any(), any());
    }

    /** #239: edit form pre-populates each existing address as a row. */
    @Test
    @WithMockUser
    void editFormPrePopulatesAddresses() throws Exception {
        UUID id = UuidFactory.newId();
        Contact existing = new Contact();
        existing.setId(id);
        existing.setOrganizationId(ORG_ID);
        existing.setFormattedName("Alice Example");
        existing.setAddresses(List.of(
                new com.majordomo.domain.model.concierge.Address(
                        UuidFactory.newId(), id, "WORK",
                        "100 Main St", "Springfield", "IL", "62701", "USA"),
                new com.majordomo.domain.model.concierge.Address(
                        UuidFactory.newId(), id, "HOME",
                        "55 Oak Ln", "Lincoln", "NE", "68501", "USA")));
        when(contactRepository.findById(id)).thenReturn(Optional.of(existing));

        org.springframework.test.web.servlet.MvcResult result =
                mvc.perform(get("/contacts/{id}/edit", id))
                        .andExpect(status().isOk())
                        .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body)
                .contains("name=\"addresses[0].label\"")
                .contains("value=\"WORK\"")
                .contains("value=\"100 Main St\"")
                .contains("value=\"Springfield\"")
                .contains("name=\"addresses[1].label\"")
                .contains("value=\"HOME\"")
                .contains("value=\"55 Oak Ln\"");
    }

    /** #239: POST /contacts persists indexed addresses[N].* params. */
    @Test
    @WithMockUser
    void createPersistsIndexedAddresses() throws Exception {
        UUID newId = UuidFactory.newId();
        Contact saved = new Contact();
        saved.setId(newId);
        saved.setOrganizationId(ORG_ID);
        when(contactUseCase.create(any(Contact.class))).thenReturn(saved);

        mvc.perform(post("/contacts")
                        .with(csrf())
                        .param("formattedName", "Alice Example")
                        .param("addresses[0].label", "WORK")
                        .param("addresses[0].street", "100 Main St")
                        .param("addresses[0].city", "Springfield")
                        .param("addresses[0].state", "IL")
                        .param("addresses[0].postalCode", "62701")
                        .param("addresses[0].country", "USA")
                        .param("addresses[1].label", "HOME")
                        .param("addresses[1].street", "55 Oak Ln")
                        .param("addresses[1].city", "Lincoln")
                        .param("addresses[1].state", "NE")
                        .param("addresses[1].postalCode", "68501")
                        .param("addresses[1].country", "USA"))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<Contact> captor = ArgumentCaptor.forClass(Contact.class);
        verify(contactUseCase).create(captor.capture());
        Contact c = captor.getValue();
        assertThat(c.getAddresses()).hasSize(2);
        assertThat(c.getAddresses().get(0).label()).isEqualTo("WORK");
        assertThat(c.getAddresses().get(0).street()).isEqualTo("100 Main St");
        assertThat(c.getAddresses().get(0).contactId()).isNull();
        assertThat(c.getAddresses().get(1).label()).isEqualTo("HOME");
        assertThat(c.getAddresses().get(1).city()).isEqualTo("Lincoln");
    }

    /** #239: addresses[N] rows with all-blank fields are dropped (treated as deletion). */
    @Test
    @WithMockUser
    void createDropsBlankAddressRows() throws Exception {
        UUID newId = UuidFactory.newId();
        Contact saved = new Contact();
        saved.setId(newId);
        saved.setOrganizationId(ORG_ID);
        when(contactUseCase.create(any(Contact.class))).thenReturn(saved);

        mvc.perform(post("/contacts")
                        .with(csrf())
                        .param("formattedName", "Alice Example")
                        .param("addresses[0].label", "")
                        .param("addresses[0].street", "")
                        .param("addresses[0].city", "")
                        .param("addresses[0].state", "")
                        .param("addresses[0].postalCode", "")
                        .param("addresses[0].country", "")
                        .param("addresses[1].label", "WORK")
                        .param("addresses[1].street", "100 Main St")
                        .param("addresses[1].city", "Springfield")
                        .param("addresses[1].state", "IL")
                        .param("addresses[1].postalCode", "62701")
                        .param("addresses[1].country", "USA"))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<Contact> captor = ArgumentCaptor.forClass(Contact.class);
        verify(contactUseCase).create(captor.capture());
        assertThat(captor.getValue().getAddresses()).hasSize(1);
        assertThat(captor.getValue().getAddresses().get(0).label()).isEqualTo("WORK");
    }

    /** #239: update replaces the address list. */
    @Test
    @WithMockUser
    void updateReplacesAddressList() throws Exception {
        UUID id = UuidFactory.newId();
        Contact existing = new Contact();
        existing.setId(id);
        existing.setOrganizationId(ORG_ID);
        existing.setFormattedName("Alice Example");
        existing.setAddresses(List.of(
                new com.majordomo.domain.model.concierge.Address(
                        UuidFactory.newId(), id, "OLD", "old", "old", "OL", "00000", "USA")));
        when(contactRepository.findById(id)).thenReturn(Optional.of(existing));
        when(contactUseCase.update(eq(id), any(Contact.class)))
                .thenAnswer(inv -> inv.getArgument(1));

        mvc.perform(post("/contacts/{id}", id)
                        .with(csrf())
                        .param("formattedName", "Alice Example")
                        .param("addresses[0].label", "NEW")
                        .param("addresses[0].street", "200 New St")
                        .param("addresses[0].city", "New City")
                        .param("addresses[0].state", "NY")
                        .param("addresses[0].postalCode", "10001")
                        .param("addresses[0].country", "USA"))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<Contact> captor = ArgumentCaptor.forClass(Contact.class);
        verify(contactUseCase).update(eq(id), captor.capture());
        assertThat(captor.getValue().getAddresses()).hasSize(1);
        assertThat(captor.getValue().getAddresses().get(0).label()).isEqualTo("NEW");
        assertThat(captor.getValue().getAddresses().get(0).street()).isEqualTo("200 New St");
    }
}
