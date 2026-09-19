package com.majordomo.domain.port.out.librarian;

import java.util.UUID;

/**
 * Outbound port for the ledger of what has already been pushed to the interest
 * graph.
 *
 * <p>Segue's {@code add_entity} is idempotent, so this is not what prevents
 * duplicate nodes there. It is majordomo's own record of what it has sent — so
 * a re-sync is a no-op locally rather than a round trip, and so "was this book
 * ever synced?" is answerable without interrogating another application.
 */
public interface InterestGraphLinkRepository {

    /**
     * Records that a book was synced under a QID.
     *
     * @param bookId the book
     * @param qid    the QID it was synced under
     * @return true if this is a new link, false if it was already recorded
     */
    boolean record(UUID bookId, String qid);

    /**
     * Whether a book has already been synced under a QID.
     *
     * @param bookId the book
     * @param qid    the QID
     * @return true when the link exists
     */
    boolean exists(UUID bookId, String qid);
}
