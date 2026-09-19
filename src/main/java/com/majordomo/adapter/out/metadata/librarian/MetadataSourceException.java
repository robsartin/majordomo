package com.majordomo.adapter.out.metadata.librarian;

/**
 * Thrown when an external book catalog cannot be reached or its answer cannot
 * be read.
 *
 * <p>Deliberately distinct from "this book is not in the catalog", which is an
 * ordinary outcome returned as an empty list. Collapsing the two would let an
 * outage look like a shelf full of unknown books, and would hide the failure
 * from the circuit breaker.
 */
public class MetadataSourceException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message what went wrong
     * @param cause   the underlying failure
     */
    public MetadataSourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
