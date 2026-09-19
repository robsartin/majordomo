package com.majordomo.domain.model.librarian;

/**
 * Where an import row came from, which is what caps how far the catalog will
 * trust it.
 *
 * <p>The distinction is not about which reader is better. It is that a CSV row
 * passed through a person before it was committed, and an extracted row did
 * not. A model reading a shelf photograph produces the same class of
 * uncertainty a person does when squinting at a spine — but at higher volume
 * and without anyone having looked.
 */
public enum ImportSource {

    /** Transcribed and reviewed by a person before import. May be trusted fully. */
    CSV,

    /**
     * Extracted from a photograph by a model, unreviewed. Capped below
     * {@link Confidence#HIGH} so it lands in the review queue rather than
     * writing itself into the catalog.
     */
    PHOTO_EXTRACTION
}
