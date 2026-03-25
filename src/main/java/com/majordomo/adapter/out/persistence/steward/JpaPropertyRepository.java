package com.majordomo.adapter.out.persistence.steward;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link PropertyEntity}, providing persistence operations
 * used by {@link PropertyRepositoryAdapter}.
 */
public interface JpaPropertyRepository extends JpaRepository<PropertyEntity, UUID> {

    List<PropertyEntity> findByOrganizationId(UUID organizationId);

    List<PropertyEntity> findByParentId(UUID parentId);

    /**
     * Returns properties for an organization ordered by ID.
     *
     * @param organizationId the organization ID
     * @param pageable       pagination information
     * @return list of property entities ordered by ID
     */
    List<PropertyEntity> findByOrganizationIdOrderById(UUID organizationId, Pageable pageable);

    /**
     * Returns properties for an organization with ID greater than the given cursor, ordered by ID.
     *
     * @param organizationId the organization ID
     * @param id             the cursor ID (exclusive lower bound)
     * @param pageable       pagination information
     * @return list of property entities after the cursor, ordered by ID
     */
    List<PropertyEntity> findByOrganizationIdAndIdGreaterThanOrderById(
            UUID organizationId, UUID id, Pageable pageable);
}
