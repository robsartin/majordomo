package com.majordomo.adapter.in.web.config;

import com.majordomo.domain.model.EntityNotFoundException;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import org.springframework.security.access.AccessDeniedException;
import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Global exception handler that translates exceptions into consistent
 * {@link ErrorResponse} JSON for API endpoints.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles entity not found exceptions.
     *
     * @param ex      the exception
     * @param request the HTTP request
     * @return 404 response with error details
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            EntityNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    /**
     * Handles illegal argument exceptions.
     *
     * @param ex      the exception
     * @param request the HTTP request
     * @return 400 response with error details
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(
            IllegalArgumentException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    /**
     * Handles validation exceptions from {@code @Valid} annotated parameters.
     *
     * @param ex      the exception
     * @param request the HTTP request
     * @return 400 response with field-level error details
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return buildResponse(HttpStatus.BAD_REQUEST, message, request);
    }

    /**
     * Handles missing required request parameters (e.g. omitted
     * {@code @RequestParam}).
     *
     * @param ex      the exception
     * @param request the HTTP request
     * @return 400 response with error details
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    /**
     * Handles access denied exceptions.
     *
     * @param ex      the exception
     * @param request the HTTP request
     * @return 403 response with error details
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    /**
     * Handles missing static resources (e.g. browser-requested {@code /favicon.ico},
     * mistyped URLs, bot probes). Returns 404 and logs at DEBUG so these don't
     * fill the log with stack traces from the catch-all handler below.
     *
     * @param ex      the exception
     * @param request the HTTP request
     * @return 404 response with a generic message
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            NoResourceFoundException ex, HttpServletRequest request) {
        LOG.debug("Resource not found: {}", request.getRequestURI());
        return buildResponse(HttpStatus.NOT_FOUND, "Resource not found", request);
    }

    /**
     * Catches all unhandled exceptions.
     *
     * @param ex      the exception
     * @param request the HTTP request
     * @return 500 response with generic error message
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(
            Exception ex, HttpServletRequest request) {
        LOG.error("Unhandled exception on {}", request.getRequestURI(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred", request);
    }

    private ResponseEntity<ErrorResponse> buildResponse(
            HttpStatus status, String message, HttpServletRequest request) {
        var body = new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                CorrelationIdContext.current()
        );
        return ResponseEntity.status(status).body(body);
    }
}
