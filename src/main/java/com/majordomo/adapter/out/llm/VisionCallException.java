package com.majordomo.adapter.out.llm;

/**
 * Thrown when an image-bearing Messages API call fails.
 */
public class VisionCallException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message what went wrong
     * @param cause   the underlying failure, or null
     */
    public VisionCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
