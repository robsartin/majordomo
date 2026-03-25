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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

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

    @Test
    void uploadValidFileSavesMetadataAndFile() {
        UUID entityId = UUID.randomUUID();
        InputStream content = new ByteArrayInputStream("test data".getBytes());

        when(fileStorage.store(anyString(), any(InputStream.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(attachmentRepository.save(any(Attachment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Attachment result = attachmentService.upload(
                "property", entityId, "photo.jpg", "image/jpeg", 1024L, content);

        assertNotNull(result.getId());
        assertEquals("property", result.getEntityType());
        assertEquals(entityId, result.getEntityId());
        assertEquals("photo.jpg", result.getFilename());
        assertEquals("image/jpeg", result.getContentType());
        assertEquals(1024L, result.getSizeBytes());
        assertNotNull(result.getStoragePath());
        assertNotNull(result.getCreatedAt());
        assertNotNull(result.getUpdatedAt());

        verify(fileStorage).store(anyString(), any(InputStream.class));
        verify(attachmentRepository).save(any(Attachment.class));
    }

    @Test
    void uploadTooLargeThrowsException() {
        UUID entityId = UUID.randomUUID();
        InputStream content = new ByteArrayInputStream("data".getBytes());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> attachmentService.upload(
                        "property", entityId, "big.pdf", "application/pdf",
                        20_000_000L, content));

        assertEquals("File size 20000000 exceeds maximum allowed 10485760", ex.getMessage());
    }

    @Test
    void uploadInvalidTypeThrowsException() {
        UUID entityId = UUID.randomUUID();
        InputStream content = new ByteArrayInputStream("data".getBytes());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> attachmentService.upload(
                        "property", entityId, "script.sh", "application/x-sh",
                        100L, content));

        assertEquals("Content type application/x-sh is not allowed", ex.getMessage());
    }

    @Test
    void downloadExistingFileReturnsContent() {
        UUID id = UUID.randomUUID();
        Attachment attachment = new Attachment();
        attachment.setId(id);
        attachment.setStoragePath("property/123/456/photo.jpg");

        InputStream expected = new ByteArrayInputStream("file content".getBytes());

        when(attachmentRepository.findById(id)).thenReturn(Optional.of(attachment));
        when(fileStorage.load("property/123/456/photo.jpg")).thenReturn(expected);

        InputStream result = attachmentService.download(id);

        assertEquals(expected, result);
        verify(fileStorage).load("property/123/456/photo.jpg");
    }

    @Test
    void downloadNonexistentAttachmentThrowsException() {
        UUID id = UUID.randomUUID();
        when(attachmentRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> attachmentService.download(id));
    }

    @Test
    void archiveExistingAttachmentSetsArchivedAt() {
        UUID id = UUID.randomUUID();
        Attachment existing = new Attachment();
        existing.setId(id);
        existing.setCreatedAt(Instant.now());
        existing.setUpdatedAt(Instant.now());

        when(attachmentRepository.findById(id)).thenReturn(Optional.of(existing));
        when(attachmentRepository.save(any(Attachment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        attachmentService.archive(id);

        ArgumentCaptor<Attachment> captor = ArgumentCaptor.forClass(Attachment.class);
        verify(attachmentRepository).save(captor.capture());
        assertNotNull(captor.getValue().getArchivedAt());
        assertNotNull(captor.getValue().getUpdatedAt());
    }

    @Test
    void archiveNonexistentAttachmentThrowsException() {
        UUID id = UUID.randomUUID();
        when(attachmentRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> attachmentService.archive(id));
    }
}
