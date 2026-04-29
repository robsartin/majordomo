package com.majordomo.adapter.in.web;

import com.majordomo.adapter.in.web.config.OAuth2UserService;
import com.majordomo.adapter.in.web.config.SecurityConfig;
import com.majordomo.domain.model.Attachment;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.port.in.ManageAttachmentUseCase;
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

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link AttachmentController}.
 */
@WebMvcTest(AttachmentController.class)
@Import(SecurityConfig.class)
class AttachmentControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean ManageAttachmentUseCase attachmentUseCase;
    @MockitoBean ApiKeyRepository apiKeyRepository;
    @MockitoBean OAuth2UserService oAuth2UserService;

    private static final UUID PROPERTY_ID = UuidFactory.newId();
    private static final UUID SCHEDULE_ID = UuidFactory.newId();
    private static final UUID RECORD_ID = UuidFactory.newId();

    /** POST property attachment returns 201 + Location. */
    @Test
    @WithMockUser
    void uploadForPropertyReturns201() throws Exception {
        Attachment saved = sample();
        when(attachmentUseCase.upload(eq("PROPERTY"), eq(PROPERTY_ID),
                any(), any(), any(Long.class), any())).thenReturn(saved);

        var file = new MockMultipartFile("file", "deed.pdf",
                "application/pdf", "body".getBytes());

        mvc.perform(multipart("/api/properties/{id}/attachments", PROPERTY_ID)
                        .file(file).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/attachments/" + saved.getId()));
    }

    /** GET property attachments returns the list. */
    @Test
    @WithMockUser
    void listForPropertyReturnsAttachments() throws Exception {
        Attachment a = sample();
        when(attachmentUseCase.list("PROPERTY", PROPERTY_ID)).thenReturn(List.of(a));

        mvc.perform(get("/api/properties/{id}/attachments", PROPERTY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(a.getId().toString()));
    }

    /** POST service-record attachment returns 201. */
    @Test
    @WithMockUser
    void uploadForServiceRecordReturns201() throws Exception {
        Attachment saved = sample();
        when(attachmentUseCase.upload(eq("SERVICE_RECORD"), eq(RECORD_ID),
                any(), any(), any(Long.class), any())).thenReturn(saved);

        var file = new MockMultipartFile("file", "receipt.pdf",
                "application/pdf", "body".getBytes());

        mvc.perform(multipart("/api/schedules/{sid}/records/{rid}/attachments",
                        SCHEDULE_ID, RECORD_ID)
                        .file(file).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/attachments/" + saved.getId()));
    }

    /** GET service-record attachments returns the list. */
    @Test
    @WithMockUser
    void listForServiceRecordReturnsAttachments() throws Exception {
        Attachment a = sample();
        when(attachmentUseCase.list("SERVICE_RECORD", RECORD_ID)).thenReturn(List.of(a));

        mvc.perform(get("/api/schedules/{sid}/records/{rid}/attachments",
                        SCHEDULE_ID, RECORD_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(a.getId().toString()));
    }

    /** GET /api/attachments/{id} streams the file with attachment Content-Disposition. */
    @Test
    @WithMockUser
    void downloadStreamsFileWithDisposition() throws Exception {
        Attachment metadata = sample();
        when(attachmentUseCase.getMetadata(metadata.getId())).thenReturn(metadata);
        when(attachmentUseCase.download(metadata.getId()))
                .thenReturn(new ByteArrayInputStream("file body".getBytes()));

        mvc.perform(get("/api/attachments/{id}", metadata.getId()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"deed.pdf\""))
                .andExpect(header().string("Content-Type", "application/pdf"));
    }

    /** DELETE /api/attachments/{id} archives and returns 204. */
    @Test
    @WithMockUser
    void archiveReturns204() throws Exception {
        UUID id = UuidFactory.newId();

        mvc.perform(delete("/api/attachments/{id}", id).with(csrf()))
                .andExpect(status().isNoContent());

        verify(attachmentUseCase).archive(id);
    }

    /** GET /api/properties/{id}/gallery returns image attachments. */
    @Test
    @WithMockUser
    void galleryReturnsImages() throws Exception {
        Attachment a = sample();
        when(attachmentUseCase.listImages("PROPERTY", PROPERTY_ID)).thenReturn(List.of(a));

        mvc.perform(get("/api/properties/{id}/gallery", PROPERTY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(a.getId().toString()));
    }

    /** PUT /api/attachments/{id}/primary marks an attachment primary. */
    @Test
    @WithMockUser
    void setAsPrimaryDelegates() throws Exception {
        Attachment a = sample();
        when(attachmentUseCase.setPrimary(a.getId())).thenReturn(a);

        mvc.perform(put("/api/attachments/{id}/primary", a.getId()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(a.getId().toString()));
    }

    /** PUT /api/attachments/{id}/order updates the sort order. */
    @Test
    @WithMockUser
    void updateOrderDelegates() throws Exception {
        Attachment a = sample();
        when(attachmentUseCase.updateSortOrder(a.getId(), 7)).thenReturn(a);

        mvc.perform(put("/api/attachments/{id}/order", a.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sortOrder\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(a.getId().toString()));

        verify(attachmentUseCase).updateSortOrder(a.getId(), 7);
    }

    private static Attachment sample() {
        Attachment a = new Attachment();
        a.setId(UuidFactory.newId());
        a.setEntityType("PROPERTY");
        a.setEntityId(PROPERTY_ID);
        a.setFilename("deed.pdf");
        a.setContentType("application/pdf");
        a.setSizeBytes(1024L);
        a.setStoragePath("path/to/deed.pdf");
        return a;
    }
}
