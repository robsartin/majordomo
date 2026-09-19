package com.majordomo.domain.model.attachment;

/**
 * The result of an extraction attempt: an outcome, and the text if there was
 * any (#298).
 *
 * @param status what happened; never {@link ExtractionStatus#PENDING}, which is
 *               a row's state before anything was attempted, not an outcome
 * @param text   the extracted text, or {@code null} for every status but
 *               {@link ExtractionStatus#EXTRACTED}
 */
public record ExtractedText(ExtractionStatus status, String text) {

    /**
     * Characters of extracted text kept per attachment.
     *
     * <p>Postgres refuses a tsvector over 1MB, and {@code attachments
     * .content_vector} is generated from this text — so an oversized document
     * would not merely search poorly, it would make the row impossible to
     * write. The cap leaves room for the worst case, where every word is
     * distinct, and is verified against a real database at that worst case.
     *
     * <p>It lives here rather than in one extractor so no extractor can bypass
     * it: every result goes through {@link #extracted(String)}.
     */
    public static final int MAX_TEXT_CHARS = 500_000;

    /**
     * Records text that was found, capped at {@link #MAX_TEXT_CHARS}.
     *
     * @param text the extracted text
     * @return an EXTRACTED result carrying it
     */
    public static ExtractedText extracted(String text) {
        return new ExtractedText(ExtractionStatus.EXTRACTED,
                text != null && text.length() > MAX_TEXT_CHARS
                        ? text.substring(0, MAX_TEXT_CHARS)
                        : text);
    }

    /**
     * Records a readable document that held no text.
     *
     * @return an EMPTY result
     */
    public static ExtractedText empty() {
        return new ExtractedText(ExtractionStatus.EMPTY, null);
    }

    /**
     * Records a type this extractor does not read.
     *
     * @return an UNSUPPORTED result
     */
    public static ExtractedText unsupported() {
        return new ExtractedText(ExtractionStatus.UNSUPPORTED, null);
    }

    /**
     * Records a document that could not be read.
     *
     * @return a FAILED result
     */
    public static ExtractedText failed() {
        return new ExtractedText(ExtractionStatus.FAILED, null);
    }
}
