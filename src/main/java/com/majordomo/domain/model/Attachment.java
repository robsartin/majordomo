package com.majordomo.domain.model;

import com.majordomo.domain.model.attachment.ExtractionStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata for a file attached to a property or service record.
 * The actual file content is stored via {@link com.majordomo.domain.port.out.FileStoragePort}.
 */
public class Attachment {

    private UUID id;
    private String entityType;
    private UUID entityId;
    private String filename;
    private String contentType;
    private long sizeBytes;
    private String storagePath;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant archivedAt;
    private boolean isPrimary;
    private int sortOrder;
    private ExtractionStatus extractionStatus = ExtractionStatus.PENDING;
    private String extractedText;
    private Instant textExtractedAt;

    public Attachment() { }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public UUID getEntityId() { return entityId; }
    public void setEntityId(UUID entityId) { this.entityId = entityId; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }

    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getArchivedAt() { return archivedAt; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }

    /**
     * Returns whether this attachment is the primary (hero) image for its entity.
     *
     * @return {@code true} if this is the primary image
     */
    public boolean isPrimary() { return isPrimary; }

    /**
     * Sets whether this attachment is the primary image for its entity.
     *
     * @param primary {@code true} to mark as primary
     */
    public void setPrimary(boolean primary) { this.isPrimary = primary; }

    /**
     * Returns the display order position of this attachment within the gallery.
     *
     * @return the zero-based sort order
     */
    public int getSortOrder() { return sortOrder; }

    /**
     * Sets the display order position of this attachment within the gallery.
     *
     * @param sortOrder the zero-based sort order
     */
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    /**
     * Returns how far text extraction has got for this attachment (#298).
     *
     * @return the extraction status, never {@code null}
     */
    public ExtractionStatus getExtractionStatus() { return extractionStatus; }

    /**
     * Sets the extraction status.
     *
     * @param extractionStatus the new status
     */
    public void setExtractionStatus(ExtractionStatus extractionStatus) {
        this.extractionStatus = extractionStatus;
    }

    /**
     * Returns the document text found in this attachment, if any.
     *
     * @return the extracted text, or {@code null} when there is none
     */
    public String getExtractedText() { return extractedText; }

    /**
     * Sets the extracted document text.
     *
     * @param extractedText the text, or {@code null}
     */
    public void setExtractedText(String extractedText) { this.extractedText = extractedText; }

    /**
     * Returns when extraction was last attempted.
     *
     * @return the timestamp, or {@code null} while still pending
     */
    public Instant getTextExtractedAt() { return textExtractedAt; }

    /**
     * Sets when extraction was attempted.
     *
     * @param textExtractedAt the timestamp
     */
    public void setTextExtractedAt(Instant textExtractedAt) {
        this.textExtractedAt = textExtractedAt;
    }
}
