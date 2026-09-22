package com.majordomo.application.envoy;

import com.majordomo.domain.model.Attachment;
import com.majordomo.domain.model.EntityType;
import com.majordomo.domain.model.attachment.ExtractionStatus;
import com.majordomo.domain.port.out.AttachmentRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Resolving the user's résumé into grounding text (#349, ADR-0028).
 *
 * <p>Most of these are failure cases on purpose. Generation without a résumé
 * produces exactly the ungrounded draft the ADR forbids, so every way of not
 * having one has to stop it — and say which way, because "no résumé", "not read
 * yet" and "could not be read" have three different fixes.
 */
@ExtendWith(MockitoExtension.class)
class ResumeSourceServiceTest {

    private static final UUID USER = UUID.randomUUID();

    @Mock
    private AttachmentRepository attachments;

    private ResumeSourceService service;

    @BeforeEach
    void setUp() {
        service = new ResumeSourceService(attachments);
    }

    @Test
    void returnsTheExtractedText() {
        given(resume("cv.pdf", ExtractionStatus.EXTRACTED, "Staff Engineer at Acme", 0));

        assertThat(service.resolve(USER)).contains("Staff Engineer at Acme");
    }

    /**
     * People upload a new CV rather than replacing the old one, and drafting
     * from a superseded résumé is a quiet way to describe the wrong person.
     */
    @Test
    void usesTheMostRecentlyUploadedResume() {
        given(
                resume("cv-2024.pdf", ExtractionStatus.EXTRACTED, "Older history", 0),
                resume("cv-2026.pdf", ExtractionStatus.EXTRACTED, "Current history", 5));

        assertThat(service.resolve(USER)).isEqualTo("Current history");
    }

    @Test
    void noResumeUploaded_saysSo() {
        given();

        assertThatThrownBy(() -> service.resolve(USER))
                .isInstanceOf(ResumeNotAvailableException.class)
                .extracting("reason")
                .isEqualTo(ResumeNotAvailableException.Reason.NONE_UPLOADED);
    }

    /**
     * The case that will actually happen. Extraction runs on a five-minute
     * sweep, so a résumé uploaded and used straight away has no text yet.
     * Proceeding would generate from nothing; the fix is to wait, and the
     * message has to say that rather than claiming no résumé exists.
     */
    @Test
    void resumeStillAwaitingExtraction_saysToWaitRatherThanThatNoneExists() {
        given(resume("cv.pdf", ExtractionStatus.PENDING, null, 0));

        assertThatThrownBy(() -> service.resolve(USER))
                .isInstanceOf(ResumeNotAvailableException.class)
                .extracting("reason")
                .isEqualTo(ResumeNotAvailableException.Reason.NOT_YET_EXTRACTED);
    }

    @Test
    void unreadableResume_saysSo() {
        given(resume("cv.pdf", ExtractionStatus.FAILED, null, 0));

        assertThatThrownBy(() -> service.resolve(USER))
                .isInstanceOf(ResumeNotAvailableException.class)
                .extracting("reason")
                .isEqualTo(ResumeNotAvailableException.Reason.UNREADABLE);
    }

    /** A blank scan: read successfully, nothing on the page. */
    @Test
    void resumeWithNoTextFound_isUnreadableRatherThanEmptyGroundingText() {
        given(resume("scan.pdf", ExtractionStatus.EMPTY, null, 0));

        assertThatThrownBy(() -> service.resolve(USER))
                .isInstanceOf(ResumeNotAvailableException.class)
                .extracting("reason")
                .isEqualTo(ResumeNotAvailableException.Reason.UNREADABLE);
    }

    /**
     * Defensive: marked extracted but holding nothing. Returning "" would hand
     * the generator empty grounding text, which is the one outcome that must
     * not reach it silently.
     */
    @Test
    void resumeMarkedExtractedButEmpty_isTreatedAsUnreadable() {
        given(resume("cv.pdf", ExtractionStatus.EXTRACTED, "   ", 0));

        assertThatThrownBy(() -> service.resolve(USER))
                .isInstanceOf(ResumeNotAvailableException.class)
                .extracting("reason")
                .isEqualTo(ResumeNotAvailableException.Reason.UNREADABLE);
    }

    /**
     * Three reasons, three fixes. If they shared a message the distinction
     * would exist only in an enum nobody reads, and someone would go looking
     * for a résumé that is sitting right there waiting to be read.
     */
    @Test
    void everyReason_carriesItsOwnMessage() {
        assertThat(List.of(ResumeNotAvailableException.Reason.values()))
                .extracting(reason -> new ResumeNotAvailableException(reason).getMessage())
                .doesNotHaveDuplicates()
                .allSatisfy(message -> assertThat(message).isNotBlank());
    }

    private void given(Attachment... resumes) {
        when(attachments.findByEntityTypeAndEntityIdAndArchivedAtIsNull(
                anyString(), any())).thenReturn(List.of(resumes));
    }

    private static Attachment resume(
            String filename, ExtractionStatus status, String text, int minutesOld) {
        Attachment a = new Attachment();
        a.setId(UUID.randomUUID());
        a.setEntityType(EntityType.USER.name());
        a.setEntityId(USER);
        a.setFilename(filename);
        a.setContentType("application/pdf");
        a.setStoragePath("USER/" + USER + "/" + filename);
        a.setCreatedAt(Instant.now().plus(minutesOld, ChronoUnit.MINUTES));
        a.setExtractionStatus(status);
        a.setExtractedText(text);
        return a;
    }
}
