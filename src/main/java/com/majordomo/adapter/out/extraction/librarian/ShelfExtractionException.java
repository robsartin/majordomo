package com.majordomo.adapter.out.extraction.librarian;

/**
 * Thrown when a shelf photograph cannot be turned into rows.
 *
 * <p>Distinct from a photograph that genuinely contains no books, which is an
 * empty list. Collapsing the two would report a failed extraction as an empty
 * shelf, and the owner would have no way to tell the difference.
 */
public class ShelfExtractionException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message what went wrong
     * @param cause   the underlying failure, or null
     */
    public ShelfExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
