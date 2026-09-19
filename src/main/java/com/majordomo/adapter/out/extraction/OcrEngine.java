package com.majordomo.adapter.out.extraction;

/**
 * Recognises text in a single raster image (#341).
 *
 * <p>A seam, not a port. It exists so the surrounding decisions — which types
 * to attempt, how many pages, what an empty answer means — are testable without
 * a Tesseract binary on the machine running the tests, while the binary itself
 * is exercised separately against the image that ships.
 */
public interface OcrEngine {

    /**
     * Recognises the text in an image.
     *
     * @param image the image bytes, in a format the engine reads
     * @return the recognised text, possibly blank
     * @throws RuntimeException if the engine could not be run or failed
     */
    String recognise(byte[] image);
}
