package com.majordomo.application;

import com.majordomo.domain.model.Attachment;
import com.majordomo.domain.model.attachment.ExtractedText;
import com.majordomo.domain.model.attachment.ExtractionStatus;
import com.majordomo.domain.port.out.AttachmentRepository;
import com.majordomo.domain.port.out.FileStoragePort;
import com.majordomo.domain.port.out.TextExtractionPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;

/**
 * Fills in the searchable text of attachments that do not have it yet (#298,
 * ADR-0026).
 *
 * <p>A sweep rather than work done during upload, because it is one mechanism
 * for two jobs: new uploads, and the backfill of everything uploaded before
 * this existed. A backfill written as a one-off script is a second code path
 * that is run once and then rots; this one runs every few minutes forever and
 * is therefore exercised constantly.
 *
 * <p>It also keeps parsing off the upload path entirely, so a corrupt PDF can
 * never turn into a failed upload of a file that was, in fact, stored fine.
 */
@Service
public class AttachmentTextExtractionService {

    private static final Logger LOG =
            LoggerFactory.getLogger(AttachmentTextExtractionService.class);

    private final AttachmentRepository attachments;
    private final FileStoragePort fileStorage;
    private final TextExtractionPort extractor;
    private final int batchSize;

    /**
     * Constructs the service with required ports and configuration.
     *
     * @param attachments the attachment metadata repository
     * @param fileStorage the file storage port the content is read from
     * @param extractor   the text extraction port
     * @param batchSize   attachments processed per sweep
     */
    public AttachmentTextExtractionService(
            AttachmentRepository attachments,
            FileStoragePort fileStorage,
            TextExtractionPort extractor,
            @Value("${majordomo.storage.extraction-batch-size:50}") int batchSize) {
        this.attachments = attachments;
        this.fileStorage = fileStorage;
        this.extractor = extractor;
        this.batchSize = batchSize;
    }

    /**
     * Extracts text for up to one batch of pending attachments.
     *
     * @return how many attachments were processed
     */
    @Scheduled(fixedDelayString = "${majordomo.storage.extraction-interval-ms:300000}")
    public int extractPending() {
        List<Attachment> pending = attachments.findPendingExtraction(batchSize);
        if (pending.isEmpty()) {
            return 0;
        }
        LOG.debug("Extracting text for {} attachment(s)", pending.size());
        for (Attachment attachment : pending) {
            record(attachment, extractOne(attachment));
        }
        return pending.size();
    }

    private ExtractedText extractOne(Attachment attachment) {
        try (InputStream content = fileStorage.load(attachment.getStoragePath())) {
            return extractor.extract(attachment.getContentType(), content);
        } catch (Exception e) {
            // Includes the row-without-a-file case, which is not hypothetical:
            // before #338 every upgrade deleted the attachment directory and
            // left these rows behind. They are permanently unreadable, so they
            // take a terminal status instead of returning in every sweep.
            LOG.warn("Could not read attachment {} at {}: {}",
                    attachment.getId(), attachment.getStoragePath(), e.toString());
            return ExtractedText.failed();
        }
    }

    private void record(Attachment attachment, ExtractedText result) {
        attachment.setExtractionStatus(result.status());
        attachment.setExtractedText(result.text());
        attachment.setTextExtractedAt(Instant.now());
        attachments.save(attachment);
        if (result.status() == ExtractionStatus.FAILED) {
            LOG.info("Attachment {} marked {} and will not be retried",
                    attachment.getId(), result.status());
        }
    }
}
