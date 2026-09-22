package com.majordomo.application.envoy;

import com.majordomo.domain.model.Attachment;
import com.majordomo.domain.model.EntityType;
import com.majordomo.domain.model.attachment.ExtractionStatus;
import com.majordomo.domain.port.in.envoy.ResolveResumeUseCase;
import com.majordomo.domain.port.out.AttachmentRepository;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Reads the user's résumé out of the attachment they uploaded (#349,
 * ADR-0028).
 *
 * <p>There is no résumé parser here and no new pipeline. A résumé is an
 * ordinary attachment on the {@code USER} record, and the extraction sweep from
 * ADR-0026 and ADR-0027 has already turned the PDF into text. This decides
 * which attachment is the résumé and whether its text is usable yet.
 */
@Service
public class ResumeSourceService implements ResolveResumeUseCase {

    private final AttachmentRepository attachments;

    /**
     * Constructs the service.
     *
     * @param attachments the attachment repository
     */
    public ResumeSourceService(AttachmentRepository attachments) {
        this.attachments = attachments;
    }

    @Override
    public String resolve(UUID userId) {
        // The repository filters archived rows, so a replaced résumé that was
        // archived rather than deleted cannot come back as the newest one.
        List<Attachment> uploaded = attachments
                .findByEntityTypeAndEntityIdAndArchivedAtIsNull(EntityType.USER.name(), userId);

        Attachment resume = uploaded.stream()
                .max(Comparator.comparing(Attachment::getCreatedAt))
                .orElseThrow(() -> new ResumeNotAvailableException(
                        ResumeNotAvailableException.Reason.NONE_UPLOADED));

        if (resume.getExtractionStatus() == ExtractionStatus.PENDING) {
            throw new ResumeNotAvailableException(
                    ResumeNotAvailableException.Reason.NOT_YET_EXTRACTED);
        }

        String text = resume.getExtractedText();
        if (resume.getExtractionStatus() != ExtractionStatus.EXTRACTED
                || text == null || text.isBlank()) {
            // Includes EXTRACTED-but-blank, which should not happen. Returning
            // it would hand the generator empty grounding text, and that is the
            // one thing that must never reach it quietly.
            throw new ResumeNotAvailableException(
                    ResumeNotAvailableException.Reason.UNREADABLE);
        }
        return text;
    }
}
