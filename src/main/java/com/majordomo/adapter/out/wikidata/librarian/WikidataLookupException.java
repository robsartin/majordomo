package com.majordomo.adapter.out.wikidata.librarian;

/**
 * Thrown when Wikidata cannot be reached or its answer cannot be read.
 *
 * <p>Distinct from "Wikidata has no such entity", which is an ordinary outcome
 * returned as an empty result. Collapsing the two would let an outage look like
 * a shelf of unidentifiable books and would hide the failure from the circuit
 * breaker.
 */
public class WikidataLookupException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message what went wrong
     * @param cause   the underlying failure
     */
    public WikidataLookupException(String message, Throwable cause) {
        super(message, cause);
    }
}
