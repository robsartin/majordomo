package com.majordomo.adapter.out.interestgraph.librarian;

/**
 * Thrown when the interest graph cannot be reached or refuses a call.
 *
 * <p>Callers are expected to treat this as "the graph did not receive this
 * book", never as "this book has nothing to contribute" — the catalog stays
 * usable when the graph is down, and a failed sync must be visibly a failure.
 */
public class InterestGraphException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message what went wrong
     * @param cause   the underlying failure, or null
     */
    public InterestGraphException(String message, Throwable cause) {
        super(message, cause);
    }
}
