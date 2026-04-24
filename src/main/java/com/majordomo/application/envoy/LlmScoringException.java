package com.majordomo.application.envoy;

/**
 * Thrown when LLM output fails validation against the active rubric, or when
 * the LLM call itself fails at the transport layer. Unchecked because the
 * controller layer maps it to a 502 Bad Gateway — callers should not attempt
 * recovery at finer granularity.
 */
public class LlmScoringException extends RuntimeException {

    /**
     * Constructs an exception with the given message.
     *
     * @param message the detail message
     */
    public LlmScoringException(String message) {
        super(message);
    }

    /**
     * Constructs an exception with the given message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public LlmScoringException(String message, Throwable cause) {
        super(message, cause);
    }
}
