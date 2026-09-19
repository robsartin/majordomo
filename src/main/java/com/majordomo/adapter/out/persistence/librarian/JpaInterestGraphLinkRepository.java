package com.majordomo.adapter.out.persistence.librarian;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data repository for {@link InterestGraphLinkEntity}.
 */
public interface JpaInterestGraphLinkRepository
        extends JpaRepository<InterestGraphLinkEntity, UUID> {

    /**
     * Whether a book has been synced under a QID.
     *
     * @param bookId      the book
     * @param wikidataQid the QID
     * @return true when the link exists
     */
    boolean existsByBookIdAndWikidataQid(UUID bookId, String wikidataQid);
}
