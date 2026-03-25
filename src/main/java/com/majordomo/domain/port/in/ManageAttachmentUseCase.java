package com.majordomo.domain.port.in;

import com.majordomo.domain.model.Attachment;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/**
 * Inbound port for managing file attachments on properties and service records.
 */
public interface ManageAttachmentUseCase {

    /**
     * Uploads a file attachment for the specified entity.
     *
     * @param entityType  the entity type (e.g. "property", "service_record")
     * @param entityId    the entity ID
     * @param filename    the original filename
     * @param contentType the MIME content type
     * @param size        the file size in bytes
     * @param content     the file content
     * @return the persisted attachment metadata
     */
    Attachment upload(String entityType, UUID entityId, String filename,
                      String contentType, long size, InputStream content);

    /**
     * Lists all attachments for the specified entity.
     *
     * @param entityType the entity type
     * @param entityId   the entity ID
     * @return list of attachment metadata
     */
    List<Attachment> list(String entityType, UUID entityId);

    /**
     * Downloads an attachment by ID, returning the file content.
     *
     * @param id the attachment ID
     * @return the file content as an input stream
     */
    InputStream download(UUID id);

    /**
     * Retrieves attachment metadata by ID.
     *
     * @param id the attachment ID
     * @return the attachment metadata
     */
    Attachment getMetadata(UUID id);

    /**
     * Archives an attachment by setting its archived_at timestamp (soft delete).
     *
     * @param id the attachment ID
     */
    void archive(UUID id);
}
