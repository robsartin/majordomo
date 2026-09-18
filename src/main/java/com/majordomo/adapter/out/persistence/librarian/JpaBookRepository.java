package com.majordomo.adapter.out.persistence.librarian;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link BookEntity}.
 */
public interface JpaBookRepository
        extends JpaRepository<BookEntity, UUID>, JpaSpecificationExecutor<BookEntity> {

    /**
     * Finds the single live book matching a normalised dedupe key within an org.
     *
     * @param normalizedKey  the normalised title-and-author key
     * @param organizationId owning org
     * @return the matching book, or empty
     */
    Optional<BookEntity> findByNormalizedKeyAndOrganizationIdAndArchivedAtIsNull(
            String normalizedKey, UUID organizationId);
}
