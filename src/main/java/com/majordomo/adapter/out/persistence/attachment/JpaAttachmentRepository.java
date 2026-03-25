package com.majordomo.adapter.out.persistence.attachment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link AttachmentEntity}.
 */
public interface JpaAttachmentRepository extends JpaRepository<AttachmentEntity, UUID> {

    /**
     * Returns all attachments for a given entity type and entity ID.
     *
     * @param entityType the entity type
     * @param entityId   the entity ID
     * @return list of attachment entities
     */
    List<AttachmentEntity> findByEntityTypeAndEntityId(String entityType, UUID entityId);
}
