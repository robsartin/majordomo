package com.majordomo.adapter.in.web.concierge;

import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.application.identity.OrganizationAccessService;
import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.concierge.Contact;
import com.majordomo.domain.port.in.concierge.ManageContactUseCase;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link ContactController}: list / get / create / update / archive.
 */
@WebMvcTest(ContactController.class)
@Import(SecurityConfig.class)
class ContactControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean ManageContactUseCase contactUseCase;
    @MockitoBean OrganizationAccessService organizationAccessService;
    @MockitoBean ApiKeyRepository apiKeyRepository;
    @MockitoBean OAuth2UserService oAuth2UserService;

    private static final UUID ORG_ID = UuidFactory.newId();

    /** GET without `q` delegates to findByOrganizationId. */
    @Test
    @WithMockUser
    void listWithoutQueryDelegatesToFindByOrg() throws Exception {
        Contact c = sample(ORG_ID);
        when(contactUseCase.findByOrganizationId(eq(ORG_ID), any(), any(Integer.class)))
                .thenReturn(new Page<>(List.of(c), null, false));

        mvc.perform(get("/api/contacts").param("organizationId", ORG_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(c.getId().toString()));

        verify(organizationAccessService).verifyAccess(ORG_ID);
    }

    /** GET with `q` delegates to search. */
    @Test
    @WithMockUser
    void listWithQueryDelegatesToSearch() throws Exception {
        when(contactUseCase.search(eq(ORG_ID), eq("smith"), any(), any(Integer.class)))
                .thenReturn(new Page<>(List.of(), null, false));

        mvc.perform(get("/api/contacts")
                        .param("organizationId", ORG_ID.toString())
                        .param("q", "smith"))
                .andExpect(status().isOk());

        verify(contactUseCase).search(eq(ORG_ID), eq("smith"), any(), any(Integer.class));
    }

    /** GET /{id} returns 200 when found. */
    @Test
    @WithMockUser
    void getByIdReturnsContact() throws Exception {
        Contact c = sample(ORG_ID);
        when(contactUseCase.findById(c.getId())).thenReturn(Optional.of(c));

        mvc.perform(get("/api/contacts/{id}", c.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(c.getId().toString()));
    }

    /** GET /{id} returns 404 when missing. */
    @Test
    @WithMockUser
    void getByIdReturns404WhenMissing() throws Exception {
        UUID id = UuidFactory.newId();
        when(contactUseCase.findById(id)).thenReturn(Optional.empty());

        mvc.perform(get("/api/contacts/{id}", id))
                .andExpect(status().isNotFound());
    }

    /** POST creates and returns 201 + Location. */
    @Test
    @WithMockUser
    void createReturns201WithLocation() throws Exception {
        Contact saved = sample(ORG_ID);
        when(contactUseCase.create(any(Contact.class))).thenReturn(saved);

        String body = """
                {
                  "organizationId": "%s",
                  "formattedName": "Jane Doe"
                }
                """.formatted(ORG_ID);

        mvc.perform(post("/api/contacts")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/contacts/" + saved.getId()));

        verify(organizationAccessService).verifyAccess(ORG_ID);
    }

    /** PUT updates and returns 200. */
    @Test
    @WithMockUser
    void updateReturnsUpdated() throws Exception {
        UUID id = UuidFactory.newId();
        Contact updated = sample(ORG_ID);
        updated.setId(id);
        updated.setFormattedName("Jane Smith");
        when(contactUseCase.update(eq(id), any(Contact.class))).thenReturn(updated);

        String body = """
                {"organizationId":"%s","formattedName":"Jane Smith"}
                """.formatted(ORG_ID);

        mvc.perform(put("/api/contacts/{id}", id)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formattedName").value("Jane Smith"));
    }

    /** DELETE archives and returns 204. */
    @Test
    @WithMockUser
    void archiveReturns204() throws Exception {
        UUID id = UuidFactory.newId();

        mvc.perform(delete("/api/contacts/{id}", id).with(csrf()))
                .andExpect(status().isNoContent());

        verify(contactUseCase).archive(id);
    }

    private static Contact sample(UUID orgId) {
        Contact c = new Contact();
        c.setId(UuidFactory.newId());
        c.setOrganizationId(orgId);
        c.setFormattedName("Jane Doe");
        return c;
    }
}
