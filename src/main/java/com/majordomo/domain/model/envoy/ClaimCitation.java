package com.majordomo.domain.model.envoy;

/**
 * One factual claim a draft makes, and the span of source text it rests on
 * (ADR-0028).
 *
 * <p>This is what makes the grounding rule enforceable rather than merely
 * requested: the verifier checks that {@code source} actually occurs in the
 * résumé, so a claim the model invented has nothing to point at.
 *
 * @param claim  the assertion made in the draft
 * @param source the span of the source document it came from
 */
public record ClaimCitation(String claim, String source) {

    /**
     * Shortest span accepted as evidence.
     *
     * <p>The check the verifier performs is "does this span appear in the
     * résumé", and a short enough span appears in every résumé ever written —
     * {@code "a"} would pass while evidencing nothing. The exact figure is a
     * judgement: low enough to admit a real phrase like {@code "Java, Spring
     * Boot"}, high enough to exclude a single common word. It bounds the
     * degenerate case; it is the verifier, not this number, that does the
     * substantive work.
     */
    public static final int MIN_SOURCE_CHARS = 12;

    /**
     * Validates the citation.
     *
     * @throws IllegalArgumentException if either side is blank, or the source
     *                                  span is too short to be evidence
     */
    public ClaimCitation {
        if (claim == null || claim.isBlank()) {
            throw new IllegalArgumentException("A citation must state its claim");
        }
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("A citation must carry its source span");
        }
        if (source.strip().length() < MIN_SOURCE_CHARS) {
            throw new IllegalArgumentException(
                    "Cited source is too short to be evidence (minimum "
                            + MIN_SOURCE_CHARS + " characters): " + source);
        }
    }
}
