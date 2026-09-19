package com.majordomo.application;

import com.majordomo.domain.model.Attachment;
import com.majordomo.domain.model.attachment.ExtractedText;
import com.majordomo.domain.model.attachment.ExtractionStatus;
import com.majordomo.domain.port.out.AttachmentRepository;
import com.majordomo.domain.port.out.FileStoragePort;
import com.majordomo.domain.port.out.TextExtractionPort;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.UncheckedIOException;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the sweep that fills in attachment text (#298).
 */
@ExtendWith(MockitoExtension.class)
class AttachmentTextExtractionServiceTest {

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private FileStoragePort fileStorage;

    @Mock
    private TextExtractionPort extractor;

    private AttachmentTextExtractionService service;

    @BeforeEach
    void setUp() {
        service = new AttachmentTextExtractionService(
                attachmentRepository, fileStorage, extractor, 50);
    }

    @Test
    void extractPending_storesTheTextAndMarksTheAttachmentExtracted() {
        when(attachmentRepository.findPendingExtraction(anyInt()))
                .thenReturn(List.of(pending("manual.pdf")));
        when(fileStorage.load(anyString())).thenReturn(new ByteArrayInputStream(new byte[] {1}));
        when(extractor.extract(anyString(), any()))
                .thenReturn(ExtractedText.extracted("Carrier furnace model 58STA"));

        assertThat(service.extractPending()).isEqualTo(1);

        Attachment saved = captureSaved();
        assertThat(saved.getExtractionStatus()).isEqualTo(ExtractionStatus.EXTRACTED);
        assertThat(saved.getExtractedText()).isEqualTo("Carrier furnace model 58STA");
        assertThat(saved.getTextExtractedAt()).isNotNull();
    }

    /**
     * Rows outliving their files is not hypothetical here — before #338 every
     * upgrade deleted the attachment directory and left the rows behind. Those
     * rows are permanently unreadable and must land on a terminal status rather
     * than returning in every sweep forever.
     */
    @Test
    void extractPending_marksFailedWhenTheFileIsGone() {
        when(attachmentRepository.findPendingExtraction(anyInt()))
                .thenReturn(List.of(pending("vanished.pdf")));
        when(fileStorage.load(anyString()))
                .thenThrow(new UncheckedIOException(new IOException("no such file")));

        service.extractPending();

        assertThat(captureSaved().getExtractionStatus()).isEqualTo(ExtractionStatus.FAILED);
    }

    /**
     * The batch has to survive its worst member. One unreadable attachment
     * stalling the sweep would block every attachment queued behind it, and the
     * queue is ordered, so it would never drain.
     */
    @Test
    void extractPending_keepsGoingAfterOneAttachmentFails() {
        Attachment broken = pending("broken.pdf");
        Attachment fine = pending("fine.pdf");
        when(attachmentRepository.findPendingExtraction(anyInt())).thenReturn(List.of(broken, fine));
        when(fileStorage.load(broken.getStoragePath()))
                .thenThrow(new UncheckedIOException(new IOException("boom")));
        when(fileStorage.load(fine.getStoragePath()))
                .thenReturn(new ByteArrayInputStream(new byte[] {1}));
        when(extractor.extract(anyString(), any())).thenReturn(ExtractedText.extracted("readable"));

        assertThat(service.extractPending()).isEqualTo(2);

        ArgumentCaptor<Attachment> captor = ArgumentCaptor.forClass(Attachment.class);
        verify(attachmentRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(Attachment::getExtractionStatus)
                .containsExactly(ExtractionStatus.FAILED, ExtractionStatus.EXTRACTED);
    }

    private Attachment captureSaved() {
        ArgumentCaptor<Attachment> captor = ArgumentCaptor.forClass(Attachment.class);
        verify(attachmentRepository).save(captor.capture());
        return captor.getValue();
    }

    private static Attachment pending(String filename) {
        Attachment attachment = new Attachment();
        attachment.setId(UUID.randomUUID());
        attachment.setEntityType("PROPERTY");
        attachment.setEntityId(UUID.randomUUID());
        attachment.setFilename(filename);
        attachment.setContentType("application/pdf");
        attachment.setStoragePath("PROPERTY/x/" + filename);
        attachment.setExtractionStatus(ExtractionStatus.PENDING);
        return attachment;
    }
}
