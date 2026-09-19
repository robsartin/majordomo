package com.majordomo.adapter.out.extraction;

/**
 * Thrown when the OCR engine could not be run, timed out, or failed (#341).
 */
public class OcrException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs the exception.
     *
     * @param message what went wrong, including the engine's own message where
     *                there is one
     */
    public OcrException(String message) {
        super(message);
    }

    /**
     * Constructs the exception with a cause.
     *
     * @param message what went wrong
     * @param cause   the underlying failure
     */
    public OcrException(String message, Throwable cause) {
        super(message, cause);
    }
}
