package com.majordomo.adapter.out.persistence.steward;

import com.majordomo.domain.model.steward.PropertyStatus;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
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

    /**
     * Searches properties by organization with a case-insensitive query and optional
     * category/status filters, ordered by ID.
     *
     * @param orgId    the organization ID
     * @param query    the search term
     * @param category optional category filter (null matches all)
     * @param status   optional status filter (null matches all)
     * @param pageable pagination information
     * @return list of matching property entities ordered by ID
     */
    @Query("SELECT p FROM PropertyEntity p WHERE p.organizationId = :orgId "
            + "AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(p.description) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(p.location) LIKE LOWER(CONCAT('%', :q, '%'))) "
            + "AND (:category IS NULL OR p.category = :category) "
            + "AND (:status IS NULL OR p.status = :status) "
            + "ORDER BY p.id")
    List<PropertyEntity> searchByOrganizationIdOrderById(
            @Param("orgId") UUID orgId, @Param("q") String query,
            @Param("category") String category, @Param("status") PropertyStatus status,
            Pageable pageable);

    /**
     * Searches properties by organization with a case-insensitive query, optional
     * category/status filters, and cursor, ordered by ID.
     *
     * @param orgId    the organization ID
     * @param query    the search term
     * @param category optional category filter (null matches all)
     * @param status   optional status filter (null matches all)
     * @param cursor   the cursor ID (exclusive lower bound)
     * @param pageable pagination information
     * @return list of matching property entities after the cursor, ordered by ID
     */
    @Query("SELECT p FROM PropertyEntity p WHERE p.organizationId = :orgId "
            + "AND p.id > :cursor "
            + "AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(p.description) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(p.location) LIKE LOWER(CONCAT('%', :q, '%'))) "
            + "AND (:category IS NULL OR p.category = :category) "
            + "AND (:status IS NULL OR p.status = :status) "
            + "ORDER BY p.id")
    List<PropertyEntity> searchByOrganizationIdAndIdGreaterThanOrderById(
            @Param("orgId") UUID orgId, @Param("q") String query,
            @Param("category") String category, @Param("status") PropertyStatus status,
            @Param("cursor") UUID cursor, Pageable pageable);

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
