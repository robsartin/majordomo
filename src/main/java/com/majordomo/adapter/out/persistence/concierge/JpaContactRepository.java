package com.majordomo.adapter.out.persistence.concierge;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ContactEntity}, providing persistence operations
 * used by {@link ContactRepositoryAdapter}.
 */
public interface JpaContactRepository extends JpaRepository<ContactEntity, UUID> {

    List<ContactEntity> findByOrganizationId(UUID organizationId);

    /**
     * Returns contacts for an organization ordered by ID.
     *
     * @param organizationId the organization ID
     * @param pageable       pagination information
     * @return list of contact entities ordered by ID
     */
    List<ContactEntity> findByOrganizationIdOrderById(UUID organizationId, Pageable pageable);

    /**
     * Returns contacts for an organization with ID greater than the given cursor, ordered by ID.
     *
     * @param organizationId the organization ID
     * @param id             the cursor ID (exclusive lower bound)
     * @param pageable       pagination information
     * @return list of contact entities after the cursor, ordered by ID
     */
    List<ContactEntity> findByOrganizationIdAndIdGreaterThanOrderById(
            UUID organizationId, UUID id, Pageable pageable);
}
