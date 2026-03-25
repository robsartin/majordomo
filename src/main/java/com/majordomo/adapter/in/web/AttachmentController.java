package com.majordomo.adapter.in.web;

import com.majordomo.domain.model.Attachment;
import com.majordomo.domain.model.EntityType;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
                EntityType.PROPERTY.name(), id, file.getOriginalFilename(),
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
        return attachmentUseCase.list(EntityType.PROPERTY.name(), id);
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
                EntityType.SERVICE_RECORD.name(), recordId, file.getOriginalFilename(),
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
        return attachmentUseCase.list(EntityType.SERVICE_RECORD.name(), recordId);
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

    /**
     * Returns the ordered image gallery for a property.
     *
     * @param id the property UUID
     * @return image attachments ordered by sort_order ascending
     */
    @GetMapping("/properties/{id}/gallery")
    public List<Attachment> getPropertyGallery(@PathVariable UUID id) {
        return attachmentUseCase.listImages(EntityType.PROPERTY.name(), id);
    }

    /**
     * Marks an attachment as the primary image for its entity.
     * Clears the primary flag on any previously-primary attachment for the same entity.
     *
     * @param id the attachment UUID
     * @return the updated attachment metadata
     */
    @PutMapping("/attachments/{id}/primary")
    public Attachment setAsPrimary(@PathVariable UUID id) {
        return attachmentUseCase.setPrimary(id);
    }

    /**
     * Updates the sort order of an attachment within its entity's gallery.
     *
     * @param id      the attachment UUID
     * @param request a JSON object containing {@code sortOrder}
     * @return the updated attachment metadata
     */
    @PutMapping("/attachments/{id}/order")
    public Attachment updateOrder(
            @PathVariable UUID id,
            @RequestBody SortOrderRequest request) {
        return attachmentUseCase.updateSortOrder(id, request.sortOrder());
    }

    /**
     * Request body for updating sort order.
     *
     * @param sortOrder the new sort order value
     */
    record SortOrderRequest(int sortOrder) { }
}
