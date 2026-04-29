package com.majordomo.adapter.in.web.concierge;

import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.concierge.Contact;
import com.majordomo.domain.port.in.concierge.ManageContactUseCase;
import com.majordomo.domain.port.out.identity.ApiKeyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link VCardController}: vCard export + import.
 */
@WebMvcTest(VCardController.class)
@Import(SecurityConfig.class)
class VCardControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean ManageContactUseCase contactUseCase;
    @MockitoBean ApiKeyRepository apiKeyRepository;
    @MockitoBean OAuth2UserService oAuth2UserService;

    private static final UUID ORG_ID = UuidFactory.newId();

    /** GET /api/contacts/{id}/vcard returns a downloadable vcf. */
    @Test
    @WithMockUser
    void exportSingleReturnsVcfAttachment() throws Exception {
        Contact c = sample(ORG_ID, "Jane Doe");
        when(contactUseCase.findById(c.getId())).thenReturn(Optional.of(c));

        var result = mvc.perform(get("/api/contacts/{id}/vcard", c.getId())
                        .accept("text/vcard"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"contact.vcf\""))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("BEGIN:VCARD").contains("Jane Doe").contains("END:VCARD");
    }

    /** GET /api/contacts/{id}/vcard returns 404 when contact missing. */
    @Test
    @WithMockUser
    void exportSingleReturns404WhenMissing() throws Exception {
        UUID id = UuidFactory.newId();
        when(contactUseCase.findById(id)).thenReturn(Optional.empty());

        mvc.perform(get("/api/contacts/{id}/vcard", id).accept("text/vcard"))
                .andExpect(status().isNotFound());
    }

    /** GET /api/contacts/export returns all org contacts as a multi-vcard file. */
    @Test
    @WithMockUser
    void exportAllReturnsMultiVcard() throws Exception {
        when(contactUseCase.findByOrganizationId(ORG_ID))
                .thenReturn(List.of(sample(ORG_ID, "Alice"), sample(ORG_ID, "Bob")));

        var result = mvc.perform(get("/api/contacts/export")
                        .param("organizationId", ORG_ID.toString())
                        .accept("text/vcard"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"contacts.vcf\""))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Alice").contains("Bob");
    }

    /** POST /api/contacts/import parses uploaded vCard, creates contacts. */
    @Test
    @WithMockUser
    void importVCardCreatesContactsFromUpload() throws Exception {
        String vcf = """
                BEGIN:VCARD
                VERSION:4.0
                FN:Casey Imported
                EMAIL:casey@example.com
                END:VCARD
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "contacts.vcf", "text/vcard", vcf.getBytes());
        Contact created = sample(ORG_ID, "Casey Imported");
        when(contactUseCase.create(any(Contact.class))).thenReturn(created);

        mvc.perform(multipart("/api/contacts/import")
                        .file(file)
                        .param("organizationId", ORG_ID.toString())
                        .with(csrf())
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk());

        verify(contactUseCase).create(any(Contact.class));
    }

    /** POST /api/contacts/import skips duplicates when skipDuplicates=true. */
    @Test
    @WithMockUser
    void importVCardSkipsDuplicates() throws Exception {
        String vcf = """
                BEGIN:VCARD
                VERSION:4.0
                FN:Existing User
                EMAIL:exists@example.com
                END:VCARD
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "contacts.vcf", "text/vcard", vcf.getBytes());

        Contact existing = sample(ORG_ID, "Existing User");
        existing.setEmails(List.of("exists@example.com"));
        when(contactUseCase.findByOrganizationId(ORG_ID)).thenReturn(List.of(existing));

        mvc.perform(multipart("/api/contacts/import")
                        .file(file)
                        .param("organizationId", ORG_ID.toString())
                        .param("skipDuplicates", "true")
                        .with(csrf())
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk());

        // Skipped — create should not be called.
        verify(contactUseCase, org.mockito.Mockito.never()).create(any(Contact.class));
    }

    private static Contact sample(UUID orgId, String name) {
        Contact c = new Contact();
        c.setId(UuidFactory.newId());
        c.setOrganizationId(orgId);
        c.setFormattedName(name);
        return c;
    }
}
