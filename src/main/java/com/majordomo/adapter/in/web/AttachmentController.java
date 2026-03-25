package com.majordomo.adapter.in.web;

import com.majordomo.domain.model.Attachment;
import com.majordomo.domain.port.in.ManageAttachmentUseCase;

import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for file attachments on properties and service records.
 *
 * <p>Provides upload, list, download, and archive (soft-delete) operations.
 * Attachments are linked to entities by type and ID, supporting both
 * properties and service records.</p>
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Attachments", description = "File attachment management")
public class AttachmentController {

    private final ManageAttachmentUseCase attachmentUseCase;

    /**
     * Constructs the controller with the attachment use case.
     *
     * @param attachmentUseCase the inbound port for attachment management
     */
    public AttachmentController(ManageAttachmentUseCase attachmentUseCase) {
        this.attachmentUseCase = attachmentUseCase;
    }

    /**
     * Uploads a file attachment for a property.
     *
     * @param id   the property UUID
     * @param file the multipart file to upload
     * @return {@code 201 Created} with the attachment metadata
     * @throws IOException if reading the file content fails
     */
    @PostMapping("/properties/{id}/attachments")
    public ResponseEntity<Attachment> uploadForProperty(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) throws IOException {
        var saved = attachmentUseCase.upload(
                "property", id, file.getOriginalFilename(),
                file.getContentType(), file.getSize(), file.getInputStream());
        return ResponseEntity
                .created(URI.create("/api/attachments/" + saved.getId()))
                .body(saved);
    }

    /**
     * Lists all attachments for a property.
     *
     * @param id the property UUID
     * @return list of attachment metadata
     */
    @GetMapping("/properties/{id}/attachments")
    public List<Attachment> listForProperty(@PathVariable UUID id) {
        return attachmentUseCase.list("property", id);
    }

    /**
     * Uploads a file attachment for a service record.
     *
     * @param scheduleId the schedule UUID
     * @param recordId   the service record UUID
     * @param file       the multipart file to upload
     * @return {@code 201 Created} with the attachment metadata
     * @throws IOException if reading the file content fails
     */
    @PostMapping("/schedules/{scheduleId}/records/{recordId}/attachments")
    public ResponseEntity<Attachment> uploadForServiceRecord(
            @PathVariable UUID scheduleId,
            @PathVariable UUID recordId,
            @RequestParam("file") MultipartFile file) throws IOException {
        var saved = attachmentUseCase.upload(
                "service_record", recordId, file.getOriginalFilename(),
                file.getContentType(), file.getSize(), file.getInputStream());
        return ResponseEntity
                .created(URI.create("/api/attachments/" + saved.getId()))
                .body(saved);
    }

    /**
     * Lists all attachments for a service record.
     *
     * @param scheduleId the schedule UUID
     * @param recordId   the service record UUID
     * @return list of attachment metadata
     */
    @GetMapping("/schedules/{scheduleId}/records/{recordId}/attachments")
    public List<Attachment> listForServiceRecord(
            @PathVariable UUID scheduleId,
            @PathVariable UUID recordId) {
        return attachmentUseCase.list("service_record", recordId);
    }

    /**
     * Downloads an attachment file by its ID.
     *
     * @param id the attachment UUID
     * @return the file content with correct content-type header
     */
    @GetMapping("/attachments/{id}")
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID id) {
        var metadata = attachmentUseCase.getMetadata(id);
        var content = attachmentUseCase.download(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(metadata.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + metadata.getFilename() + "\"")
                .body(new InputStreamResource(content));
    }

    /**
     * Archives an attachment by setting its archived_at timestamp (soft delete).
     *
     * @param id the attachment UUID
     * @return {@code 204 No Content} on success
     */
    @DeleteMapping("/attachments/{id}")
    public ResponseEntity<Void> archive(@PathVariable UUID id) {
        attachmentUseCase.archive(id);
        return ResponseEntity.noContent().build();
    }
}
