package com.majordomo.adapter.out.persistence.attachment;

import com.majordomo.domain.model.Attachment;
import com.majordomo.domain.port.out.AttachmentRepository;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence adapter that fulfills the {@link AttachmentRepository}
 * output port by delegating to {@link JpaAttachmentRepository}.
 */
@Repository
public class AttachmentRepositoryAdapter implements AttachmentRepository {

    private final JpaAttachmentRepository jpa;

    /**
     * Constructs the adapter with the JPA repository.
     *
     * @param jpa the Spring Data JPA repository
     */
    public AttachmentRepositoryAdapter(JpaAttachmentRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Attachment save(Attachment attachment) {
        var entity = AttachmentMapper.toEntity(attachment);
        return AttachmentMapper.toDomain(jpa.save(entity));
    }

    @Override
    public Optional<Attachment> findById(UUID id) {
        return jpa.findById(id).map(AttachmentMapper::toDomain);
    }

    @Override
    public List<Attachment> findByEntityTypeAndEntityId(String entityType, UUID entityId) {
        return jpa.findByEntityTypeAndEntityId(entityType, entityId)
                .stream()
                .map(AttachmentMapper::toDomain)
                .toList();
    }

    @Override
    public List<Attachment> findImagesByEntityTypeAndEntityId(String entityType, UUID entityId) {
        return jpa.findImagesByEntityTypeAndEntityId(entityType, entityId)
                .stream()
                .map(AttachmentMapper::toDomain)
                .toList();
    }

    @Override
    public List<Attachment> findByEntityTypeAndEntityIdAndArchivedAtIsNull(
            String entityType, UUID entityId) {
        return jpa.findByEntityTypeAndEntityIdAndArchivedAtIsNull(entityType, entityId)
                .stream()
                .map(AttachmentMapper::toDomain)
                .toList();
    }
}
