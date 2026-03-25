package com.majordomo.domain.port.out;

import com.majordomo.domain.model.Attachment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for attachment metadata persistence.
 */
public interface AttachmentRepository {

    /**
     * Persists an attachment, inserting or updating as needed.
     *
     * @param attachment the attachment to save
     * @return the saved attachment
     */
    Attachment save(Attachment attachment);

    /**
     * Retrieves an attachment by its unique identifier.
     *
     * @param id the attachment ID
     * @return the attachment, or empty if not found
     */
    Optional<Attachment> findById(UUID id);

    /**
     * Returns all attachments for a given entity type and entity ID.
     *
     * @param entityType the type of entity (e.g. "property", "service_record")
     * @param entityId   the entity ID
     * @return list of attachments, or an empty list if none exist
     */
    List<Attachment> findByEntityTypeAndEntityId(String entityType, UUID entityId);
}
