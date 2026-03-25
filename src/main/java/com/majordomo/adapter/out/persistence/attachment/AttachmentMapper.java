package com.majordomo.adapter.out.persistence.attachment;

import com.majordomo.domain.model.Attachment;

final class AttachmentMapper {

    private AttachmentMapper() { }

    static AttachmentEntity toEntity(Attachment attachment) {
        var entity = new AttachmentEntity();
        entity.setId(attachment.getId());
        entity.setEntityType(attachment.getEntityType());
        entity.setEntityId(attachment.getEntityId());
        entity.setFilename(attachment.getFilename());
        entity.setContentType(attachment.getContentType());
        entity.setSizeBytes(attachment.getSizeBytes());
        entity.setStoragePath(attachment.getStoragePath());
        entity.setCreatedAt(attachment.getCreatedAt());
        entity.setUpdatedAt(attachment.getUpdatedAt());
        entity.setArchivedAt(attachment.getArchivedAt());
        return entity;
    }

    static Attachment toDomain(AttachmentEntity entity) {
        var attachment = new Attachment();
        attachment.setId(entity.getId());
        attachment.setEntityType(entity.getEntityType());
        attachment.setEntityId(entity.getEntityId());
        attachment.setFilename(entity.getFilename());
        attachment.setContentType(entity.getContentType());
        attachment.setSizeBytes(entity.getSizeBytes());
        attachment.setStoragePath(entity.getStoragePath());
        attachment.setCreatedAt(entity.getCreatedAt());
        attachment.setUpdatedAt(entity.getUpdatedAt());
        attachment.setArchivedAt(entity.getArchivedAt());
        return attachment;
    }
}
