package com.majordomo.application;

import com.majordomo.domain.model.Attachment;
import com.majordomo.domain.model.EntityNotFoundException;
import com.majordomo.domain.port.in.ManageAttachmentUseCase;
import com.majordomo.domain.port.out.AttachmentRepository;
import com.majordomo.domain.port.out.FileStoragePort;

import com.majordomo.domain.model.UuidFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Application service implementing attachment management use cases.
 * Validates file size and content type before delegating to storage and persistence ports.
 */
@Service
public class AttachmentService implements ManageAttachmentUseCase {

    private final AttachmentRepository attachmentRepository;
    private final FileStoragePort fileStorage;
    private final long maxFileSize;
    private final Set<String> allowedTypes;

    /**
     * Constructs the service with required ports and configuration.
     *
     * @param attachmentRepository the outbound port for attachment metadata persistence
     * @param fileStorage          the outbound port for file storage operations
     * @param maxFileSize          the maximum allowed file size in bytes
     * @param allowedTypes         comma-separated list of allowed MIME content types
     */
    public AttachmentService(
            AttachmentRepository attachmentRepository,
            FileStoragePort fileStorage,
            @Value("${majordomo.storage.max-file-size:10485760}") long maxFileSize,
            @Value("${majordomo.storage.allowed-types:image/jpeg,image/png,application/pdf,text/plain}")
            String allowedTypes) {
        this.attachmentRepository = attachmentRepository;
        this.fileStorage = fileStorage;
        this.maxFileSize = maxFileSize;
        this.allowedTypes = Set.of(allowedTypes.split(","));
    }

    @Override
    public Attachment upload(String entityType, UUID entityId, String filename,
                             String contentType, long size, InputStream content) {
        if (size > maxFileSize) {
            throw new IllegalArgumentException(
                    "File size " + size + " exceeds maximum allowed " + maxFileSize);
        }
        if (!allowedTypes.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Content type " + contentType + " is not allowed");
        }

        UUID id = UuidFactory.newId();
        String path = entityType + "/" + entityId + "/" + id + "/" + filename;
        fileStorage.store(path, content);

        Attachment attachment = new Attachment();
        attachment.setId(id);
        attachment.setEntityType(entityType);
        attachment.setEntityId(entityId);
        attachment.setFilename(filename);
        attachment.setContentType(contentType);
        attachment.setSizeBytes(size);
        attachment.setStoragePath(path);

        return attachmentRepository.save(attachment);
    }

    @Override
    public List<Attachment> list(String entityType, UUID entityId) {
        return attachmentRepository.findByEntityTypeAndEntityId(entityType, entityId);
    }

    @Override
    public InputStream download(UUID id) {
        Attachment attachment = attachmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Attachment", id));
        return fileStorage.load(attachment.getStoragePath());
    }

    @Override
    public Attachment getMetadata(UUID id) {
        return attachmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Attachment", id));
    }

    @Override
    public void archive(UUID id) {
        Attachment existing = attachmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Attachment", id));
        existing.setArchivedAt(Instant.now());
        attachmentRepository.save(existing);
    }

    @Override
    public List<Attachment> listImages(String entityType, UUID entityId) {
        return attachmentRepository.findImagesByEntityTypeAndEntityId(entityType, entityId);
    }

    @Override
    public Attachment setPrimary(UUID id) {
        Attachment target = attachmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Attachment", id));

        // Clear primary flag on all current images for the same entity
        attachmentRepository
                .findByEntityTypeAndEntityIdAndArchivedAtIsNull(
                        target.getEntityType(), target.getEntityId())
                .stream()
                .filter(Attachment::isPrimary)
                .forEach(a -> {
                    a.setPrimary(false);
                    a.setUpdatedAt(Instant.now());
                    attachmentRepository.save(a);
                });

        target.setPrimary(true);
        target.setUpdatedAt(Instant.now());
        return attachmentRepository.save(target);
    }

    @Override
    public Attachment updateSortOrder(UUID id, int sortOrder) {
        Attachment existing = attachmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Attachment", id));
        existing.setSortOrder(sortOrder);
        existing.setUpdatedAt(Instant.now());
        return attachmentRepository.save(existing);
    }
}
