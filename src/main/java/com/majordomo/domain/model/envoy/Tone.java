package com.majordomo.domain.model.envoy;

/**
 * Register for a generated draft, chosen per request rather than stored
 * (ADR-0028) — the voice for a message to a founder is not the voice for a
 * formal letter to a bank, and both happen in the same week.
 */
public enum Tone {

    /** Plain and brief; no throat-clearing. */
    DIRECT,

    /** Conversational and personable, for a message to a human being. */
    WARM,

    /** Conventional business register, for a formal application. */
    FORMAL
}
