package com.majordomo.application.envoy;

import java.util.List;

/**
 * Thrown when a generated draft says something its sources do not support
 * (#350, ADR-0028).
 *
 * <p>The draft is discarded rather than stored with a warning attached. A
 * warning on a document someone is about to send under their own name is a note
 * that gets scrolled past, not a control.
 */
public class UngroundedDraftException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<String> problems;

    /**
     * Constructs the exception.
     *
     * @param problems every way the draft was not grounded
     */
    public UngroundedDraftException(List<String> problems) {
        super("Draft rejected as ungrounded: " + String.join("; ", problems));
        this.problems = List.copyOf(problems);
    }

    /**
     * Returns the individual grounding failures.
     *
     * @return the problems, in the order they were found
     */
    public List<String> problems() {
        return problems;
    }
}
