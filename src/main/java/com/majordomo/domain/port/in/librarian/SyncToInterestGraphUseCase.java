package com.majordomo.domain.port.in.librarian;

import java.util.UUID;

/**
 * Inbound port for pushing a book's authors into the interest graph.
 *
 * <p>Every cataloged book syncs, carrying its rating as an attribute rather
 * than being filtered on it (ADR-0023): the graph models its own taste rating
 * and is better placed to weight what it receives than this service is to
 * withhold it. Books without a resolved Wikidata QID are skipped, since the QID
 * is the join key.
 */
public interface SyncToInterestGraphUseCase {

    /**
     * Syncs one book's authors.
     *
     * @param bookId         the book to sync
     * @param organizationId owning org
     * @return true when the book was synced, false when it was skipped for want
     *         of a QID
     */
    boolean sync(UUID bookId, UUID organizationId);
}
