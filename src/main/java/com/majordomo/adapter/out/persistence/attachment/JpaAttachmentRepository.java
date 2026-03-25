package com.majordomo.adapter.out.persistence.attachment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Returns all non-archived attachments for a given entity type and entity ID.
     *
     * @param entityType the entity type
     * @param entityId   the entity ID
     * @return list of non-archived attachment entities
     */
    List<AttachmentEntity> findByEntityTypeAndEntityIdAndArchivedAtIsNull(
            String entityType, UUID entityId);

    /**
     * Returns image attachments for the given entity, ordered by sort_order ascending.
     * Only non-archived attachments with a content type starting with {@code image/}
     * are included.
     *
     * @param type the entity type
     * @param id   the entity ID
     * @return ordered list of image attachment entities
     */
    @Query("SELECT a FROM AttachmentEntity a WHERE a.entityType = :type AND a.entityId = :id "
        + "AND a.contentType LIKE 'image/%' AND a.archivedAt IS NULL ORDER BY a.sortOrder")
    List<AttachmentEntity> findImagesByEntityTypeAndEntityId(
            @Param("type") String type, @Param("id") UUID id);
}
