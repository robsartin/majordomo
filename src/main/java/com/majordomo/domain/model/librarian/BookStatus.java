package com.majordomo.domain.model.librarian;

/**
 * Describes where a book currently sits in its lifecycle on the shelf.
 */
public enum BookStatus {
    /** On the shelf and owned, but not necessarily read. */
    OWNED,
    /** Owned and read through. */
    READ,
    /** Not owned; on the wish list. */
    WANT,
    /** Owned but currently lent out. */
    LOANED
}
