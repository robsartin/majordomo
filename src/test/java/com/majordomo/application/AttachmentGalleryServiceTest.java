package com.majordomo.application;

import com.majordomo.domain.model.Attachment;
import com.majordomo.domain.model.EntityNotFoundException;
import com.majordomo.domain.port.out.AttachmentRepository;
import com.majordomo.domain.port.out.FileStoragePort;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for gallery ordering and primary-image selection within
 * {@link AttachmentService}.
 */
@ExtendWith(MockitoExtension.class)
class AttachmentGalleryServiceTest {

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private FileStoragePort fileStorage;

    private AttachmentService attachmentService;

    @BeforeEach
    void setUp() {
        attachmentService = new AttachmentService(
                attachmentRepository, fileStorage,
                10_485_760L,
                "image/jpeg,image/png,application/pdf,text/plain");
    }

    // -----------------------------------------------------------------------
    // listImages
    // -----------------------------------------------------------------------

    @Test
    void listImagesDelegatesToRepositoryAndReturnsOrderedList() {
        UUID entityId = UUID.randomUUID();
        Attachment img1 = imageAttachment(entityId, 0);
        Attachment img2 = imageAttachment(entityId, 1);

        when(attachmentRepository.findImagesByEntityTypeAndEntityId("property", entityId))
                .thenReturn(List.of(img1, img2));

        List<Attachment> result = attachmentService.listImages("property", entityId);

        assertEquals(2, result.size());
        assertEquals(0, result.get(0).getSortOrder());
        assertEquals(1, result.get(1).getSortOrder());
        verify(attachmentRepository).findImagesByEntityTypeAndEntityId("property", entityId);
    }

    @Test
    void listImagesReturnsEmptyListWhenNoneExist() {
        UUID entityId = UUID.randomUUID();
        when(attachmentRepository.findImagesByEntityTypeAndEntityId("property", entityId))
                .thenReturn(List.of());

        List<Attachment> result = attachmentService.listImages("property", entityId);

        assertTrue(result.isEmpty());
    }

    // -----------------------------------------------------------------------
    // setPrimary
    // -----------------------------------------------------------------------

    @Test
    void setPrimaryMarkesTargetAndClearsPreviousPrimary() {
        UUID entityId = UUID.randomUUID();
        UUID oldPrimaryId = UUID.randomUUID();
        UUID newPrimaryId = UUID.randomUUID();

        Attachment oldPrimary = imageAttachment(entityId, 0);
        oldPrimary.setId(oldPrimaryId);
        oldPrimary.setPrimary(true);

        Attachment newPrimary = imageAttachment(entityId, 1);
        newPrimary.setId(newPrimaryId);
        newPrimary.setPrimary(false);

        when(attachmentRepository.findById(newPrimaryId))
                .thenReturn(Optional.of(newPrimary));
        when(attachmentRepository.findByEntityTypeAndEntityIdAndArchivedAtIsNull(
                "property", entityId))
                .thenReturn(List.of(oldPrimary, newPrimary));
        when(attachmentRepository.save(any(Attachment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Attachment result = attachmentService.setPrimary(newPrimaryId);

        assertTrue(result.isPrimary());
        assertFalse(oldPrimary.isPrimary());

        // save called: once to clear old primary, once to set new primary
        ArgumentCaptor<Attachment> captor = ArgumentCaptor.forClass(Attachment.class);
        verify(attachmentRepository, times(2)).save(captor.capture());
        List<Attachment> saved = captor.getAllValues();
        // first save clears old primary
        assertFalse(saved.get(0).isPrimary());
        // second save sets new primary
        assertTrue(saved.get(1).isPrimary());
    }

    @Test
    void setPrimaryWhenNoPreviousPrimaryOnlySetsTarget() {
        UUID entityId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        Attachment target = imageAttachment(entityId, 0);
        target.setId(targetId);
        target.setPrimary(false);

        Attachment other = imageAttachment(entityId, 1);
        other.setId(UUID.randomUUID());
        other.setPrimary(false);

        when(attachmentRepository.findById(targetId))
                .thenReturn(Optional.of(target));
        when(attachmentRepository.findByEntityTypeAndEntityIdAndArchivedAtIsNull(
                "property", entityId))
                .thenReturn(List.of(target, other));
        when(attachmentRepository.save(any(Attachment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Attachment result = attachmentService.setPrimary(targetId);

        assertTrue(result.isPrimary());
        // only one save — the target itself
        verify(attachmentRepository, times(1)).save(any());
    }

    @Test
    void setPrimaryNonexistentAttachmentThrowsException() {
        UUID id = UUID.randomUUID();
        when(attachmentRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> attachmentService.setPrimary(id));
    }

    // -----------------------------------------------------------------------
    // updateSortOrder
    // -----------------------------------------------------------------------

    @Test
    void updateSortOrderPersistsNewValue() {
        UUID id = UUID.randomUUID();
        Attachment existing = imageAttachment(UUID.randomUUID(), 5);
        existing.setId(id);

        when(attachmentRepository.findById(id)).thenReturn(Optional.of(existing));
        when(attachmentRepository.save(any(Attachment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Attachment result = attachmentService.updateSortOrder(id, 3);

        assertEquals(3, result.getSortOrder());
        ArgumentCaptor<Attachment> captor = ArgumentCaptor.forClass(Attachment.class);
        verify(attachmentRepository).save(captor.capture());
        assertEquals(3, captor.getValue().getSortOrder());
    }

    @Test
    void updateSortOrderNonexistentAttachmentThrowsException() {
        UUID id = UUID.randomUUID();
        when(attachmentRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> attachmentService.updateSortOrder(id, 0));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Attachment imageAttachment(UUID entityId, int sortOrder) {
        Attachment a = new Attachment();
        a.setId(UUID.randomUUID());
        a.setEntityType("property");
        a.setEntityId(entityId);
        a.setFilename("photo.jpg");
        a.setContentType("image/jpeg");
        a.setSizeBytes(1024L);
        a.setStoragePath("property/" + entityId + "/photo.jpg");
        a.setCreatedAt(Instant.now());
        a.setUpdatedAt(Instant.now());
        a.setSortOrder(sortOrder);
        return a;
    }
}
