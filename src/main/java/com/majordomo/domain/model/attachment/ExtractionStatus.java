package com.majordomo.domain.model.attachment;

/**
 * Outcome of trying to read an attachment's text (#298).
 *
 * <p>Every state is distinct on purpose. Collapsing them into a nullable text
 * column would make "not looked at yet", "looked at, nothing there" and "could
 * not be read" indistinguishable, so the sweep would either retry work that
 * will never succeed or skip work it never did.
 */
public enum ExtractionStatus {

    /** Not yet processed. The sweep's queue is exactly this set. */
    PENDING,

    /** Text was found and stored. */
    EXTRACTED,

    /**
     * Read successfully, but there was no text — a scanned document, or a photo
     * of one. Terminal for now, and the set worth revisiting when OCR arrives.
     */
    EMPTY,

    /** Not a type this extractor reads, such as an image. Terminal. */
    UNSUPPORTED,

    /**
     * Unreadable — corrupt, or not the type it claims. Terminal rather than
     * retried: nothing about a later attempt would differ, and a permanently
     * failing row would otherwise reappear in every sweep forever.
     */
    FAILED
}
