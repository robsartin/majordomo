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
     * Records text that was found.
     *
     * @param text the extracted text
     * @return an EXTRACTED result carrying it
     */
    public static ExtractedText extracted(String text) {
        return new ExtractedText(ExtractionStatus.EXTRACTED, text);
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
