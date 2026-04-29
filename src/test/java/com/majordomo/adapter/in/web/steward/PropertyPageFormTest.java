package com.majordomo.adapter.in.web.steward;

import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.application.identity.CurrentOrganizationResolver;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.identity.User;
import com.majordomo.domain.port.in.ManageAttachmentUseCase;
import com.majordomo.domain.port.in.concierge.ManageContactUseCase;
import com.majordomo.domain.port.in.herald.ManageScheduleUseCase;
import com.majordomo.domain.port.in.steward.ManagePropertyUseCase;
import com.majordomo.domain.port.out.herald.ServiceRecordRepository;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;
import com.majordomo.domain.port.out.identity.MembershipRepository;
import com.majordomo.domain.port.out.identity.UserRepository;
import com.majordomo.domain.port.out.steward.PropertyContactRepository;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Slice tests for the property add/edit forms (#225). Strict TDD per ADR-0003.
 */
@WebMvcTest(PropertyPageController.class)
@Import(SecurityConfig.class)
class PropertyPageFormTest {

    @Autowired MockMvc mvc;

    @MockitoBean ManagePropertyUseCase propertyUseCase;
    @MockitoBean ManageScheduleUseCase scheduleUseCase;
    @MockitoBean ManageContactUseCase contactUseCase;
    @MockitoBean ManageAttachmentUseCase attachmentUseCase;
    @MockitoBean PropertyContactRepository propertyContactRepository;
    @MockitoBean PropertyRepository propertyRepository;
    @MockitoBean ServiceRecordRepository serviceRecordRepository;
    @MockitoBean CurrentOrganizationResolver currentOrg;
    @MockitoBean UserRepository userRepository;
    @MockitoBean MembershipRepository membershipRepository;
    @MockitoBean ApiKeyRepository apiKeyRepository;
    @MockitoBean OAuth2UserService oAuth2UserService;

    private static final UUID ORG_ID = UuidFactory.newId();

    @BeforeEach
    void seedAuth() {
        User user = new User(UuidFactory.newId(), "robsartin", "rob@example.com");
        when(currentOrg.resolve(any(UserDetails.class)))
                .thenReturn(new CurrentOrganizationResolver.Resolved(user, ORG_ID));
    }

    /** Cycle 1: GET /properties/new returns 200 and renders property-form view. */
    @Test
    @WithMockUser
    void newFormRenders() throws Exception {
        mvc.perform(get("/properties/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("property-form"));
    }

    /** Cycle 2: POST /properties creates the property and redirects to /properties/{id}. */
    @Test
    @WithMockUser
    void createPersistsAndRedirectsToDetail() throws Exception {
        UUID newId = UuidFactory.newId();
        com.majordomo.domain.model.steward.Property saved =
                new com.majordomo.domain.model.steward.Property();
        saved.setId(newId);
        saved.setOrganizationId(ORG_ID);
        saved.setName("Beach House");
        when(propertyUseCase.create(any(com.majordomo.domain.model.steward.Property.class)))
                .thenReturn(saved);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/properties")
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.csrf())
                        .param("name", "Beach House")
                        .param("category", "vacation"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/properties/" + newId));

        org.mockito.ArgumentCaptor<com.majordomo.domain.model.steward.Property> captor =
                org.mockito.ArgumentCaptor.forClass(
                        com.majordomo.domain.model.steward.Property.class);
        org.mockito.Mockito.verify(propertyUseCase).create(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getName()).isEqualTo("Beach House");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getCategory()).isEqualTo("vacation");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getOrganizationId()).isEqualTo(ORG_ID);
    }

    /** Cycle 3: POST /properties with blank name re-renders the form with error + field state. */
    @Test
    @WithMockUser
    void createWithBlankNameRendersFormWithError() throws Exception {
        org.springframework.test.web.servlet.MvcResult result = mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/properties")
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.csrf())
                        .param("name", "")
                        .param("category", "vacation"))
                .andExpect(status().isOk())
                .andExpect(view().name("property-form"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body).contains("Name is required.");
        // Field state is echoed back so the form keeps state.
        org.assertj.core.api.Assertions.assertThat(body).contains("value=\"vacation\"");
        org.mockito.Mockito.verify(propertyUseCase, org.mockito.Mockito.never()).create(any());
    }

    /** Cycle 4: GET /properties/{id}/edit pre-populates the form fields from the existing property. */
    @Test
    @WithMockUser
    void editFormPrePopulates() throws Exception {
        UUID id = UuidFactory.newId();
        com.majordomo.domain.model.steward.Property existing =
                new com.majordomo.domain.model.steward.Property();
        existing.setId(id);
        existing.setOrganizationId(ORG_ID);
        existing.setName("Beach House");
        existing.setCategory("vacation");
        when(propertyUseCase.findById(id)).thenReturn(java.util.Optional.of(existing));

        org.springframework.test.web.servlet.MvcResult result = mvc.perform(
                get("/properties/{id}/edit", id))
                .andExpect(status().isOk())
                .andExpect(view().name("property-form"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body).contains("Edit property");
        org.assertj.core.api.Assertions.assertThat(body).contains("value=\"Beach House\"");
        org.assertj.core.api.Assertions.assertThat(body).contains("value=\"vacation\"");
    }

    /** Cycle 4b: GET /properties/{id}/edit returns 404 when missing. */
    @Test
    @WithMockUser
    void editFormReturns404WhenMissing() throws Exception {
        UUID id = UuidFactory.newId();
        when(propertyUseCase.findById(id)).thenReturn(java.util.Optional.empty());

        mvc.perform(get("/properties/{id}/edit", id))
                .andExpect(status().isNotFound());
    }

    /** Cycle 5: POST /properties/{id} updates the property and redirects to detail. */
    @Test
    @WithMockUser
    void updatePersistsAndRedirectsToDetail() throws Exception {
        UUID id = UuidFactory.newId();
        com.majordomo.domain.model.steward.Property existing =
                new com.majordomo.domain.model.steward.Property();
        existing.setId(id);
        existing.setOrganizationId(ORG_ID);
        existing.setName("Old name");
        existing.setCategory("old-cat");
        when(propertyUseCase.findById(id)).thenReturn(java.util.Optional.of(existing));
        when(propertyUseCase.update(org.mockito.ArgumentMatchers.eq(id),
                any(com.majordomo.domain.model.steward.Property.class)))
                .thenAnswer(inv -> inv.getArgument(1));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/properties/{id}", id)
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.csrf())
                        .param("name", "New name")
                        .param("category", "new-cat"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/properties/" + id));

        org.mockito.ArgumentCaptor<com.majordomo.domain.model.steward.Property> captor =
                org.mockito.ArgumentCaptor.forClass(
                        com.majordomo.domain.model.steward.Property.class);
        org.mockito.Mockito.verify(propertyUseCase).update(
                org.mockito.ArgumentMatchers.eq(id), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getName()).isEqualTo("New name");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getCategory()).isEqualTo("new-cat");
    }
}
