package com.majordomo.domain.model.librarian;

/**
 * How much to trust a book's current metadata.
 *
 * <p>Set at import from the transcription notes — a spine read cleanly is not
 * the same as an author filled in from memory — and re-set by enrichment when
 * an external match is applied. Anything below {@link #HIGH} is what feeds the
 * review queue rather than being written straight to the catalog.
 *
 * <p>Deliberately separate from the Envoy enum of the same name: each vertical
 * slice owns its own types, and these two grade different things.
 */
public enum Confidence {
    /** Read directly and unambiguously, or confirmed by a strong external match. */
    HIGH,
    /** Partially legible, or matched with some ambiguity. */
    MEDIUM,
    /** Filled in from knowledge, obscured, or explicitly uncertain. */
    LOW
}
