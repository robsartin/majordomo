package com.majordomo.adapter.out.persistence.steward;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link PropertyEntity}, providing persistence operations
 * used by {@link PropertyRepositoryAdapter}.
 */
public interface JpaPropertyRepository extends JpaRepository<PropertyEntity, UUID>,
        JpaSpecificationExecutor<PropertyEntity> {

    /**
     * Returns all properties for an organization (unbounded, for internal use).
     *
     * @param organizationId the organization ID
     * @return list of all property entities for the organization
     */
    List<PropertyEntity> findByOrganizationId(UUID organizationId);

    /**
     * Returns all properties with the given parent ID.
     *
     * @param parentId the parent property ID
     * @return list of child property entities
     */
    List<PropertyEntity> findByParentId(UUID parentId);

    /**
     * Returns all properties whose warranty expires before the given date
     * and whose warranty notification has not yet been sent.
     *
     * @param date the upper bound date for warranty expiration
     * @return list of matching property entities
     */
    @Query("SELECT p FROM PropertyEntity p WHERE p.warrantyExpiresOn IS NOT NULL "
            + "AND p.warrantyExpiresOn < :date "
            + "AND p.warrantyNotificationSentAt IS NULL")
    List<PropertyEntity> findWithWarrantyExpiringBefore(@Param("date") LocalDate date);
}
