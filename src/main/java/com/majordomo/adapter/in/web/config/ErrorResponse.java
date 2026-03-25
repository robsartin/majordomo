package com.majordomo.adapter.in.web.config;

import java.time.Instant;

/**
 * Standard error response returned by the API for all error conditions.
 *
 * @param timestamp when the error occurred
 * @param status    HTTP status code
 * @param error     HTTP status reason phrase
 * @param message   human-readable error description
 * @param path      the request path that caused the error
 */
public record ErrorResponse(
    Instant timestamp,
    int status,
    String error,
    String message,
    String path
) { }
