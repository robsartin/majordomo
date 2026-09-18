package com.majordomo.domain.port.out.librarian;

import com.majordomo.domain.model.librarian.Book;

/**
 * Outbound port for the personal interest graph that books feed into.
 *
 * <p>The graph is a separate application that owns its own storage, reached
 * through an ingest endpoint rather than by writing its database directly
 * (ADR-0023). Implementations must be idempotent — syncing the same book twice
 * creates no duplicate nodes or edges — and must degrade gracefully, since the
 * catalog stays useful when the graph is unreachable.
 */
public interface InterestGraphPort {

    /**
     * Pushes a book's authors into the graph, carrying its rating as an
     * attribute for the graph to weight.
     *
     * @param book the book to push; must have a resolved Wikidata QID
     */
    void syncBook(Book book);
}
