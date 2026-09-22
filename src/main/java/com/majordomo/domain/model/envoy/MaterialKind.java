package com.majordomo.domain.model.envoy;

/**
 * The kinds of application material Envoy drafts (#290, ADR-0028).
 */
public enum MaterialKind {

    /** Long-form letter tailored to one posting. */
    COVER_LETTER,

    /** A few sentences for a LinkedIn message, recruiter reply or email. */
    INTRO_MESSAGE,

    /** Drafts for the free-text boxes application forms ask for. */
    SCREENING_ANSWERS,

    /**
     * Existing résumé bullets rewritten in the posting's language.
     *
     * <p>The riskiest kind: it edits claims about history rather than framing
     * them, so a rewrite that changes a number changes a fact.
     */
    RESUME_BULLETS
}
