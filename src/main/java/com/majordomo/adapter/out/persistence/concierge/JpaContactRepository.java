package com.majordomo.adapter.out.persistence.concierge;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Searches contacts by organization with a case-insensitive query, ordered by ID.
     *
     * @param orgId    the organization ID
     * @param query    the search term
     * @param pageable pagination information
     * @return list of matching contact entities ordered by ID
     */
    @Query("SELECT c FROM ContactEntity c WHERE c.organizationId = :orgId "
            + "AND (LOWER(c.formattedName) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(c.givenName) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(c.familyName) LIKE LOWER(CONCAT('%', :q, '%')))"
            + " ORDER BY c.id")
    List<ContactEntity> searchByOrganizationIdOrderById(
            @Param("orgId") UUID orgId, @Param("q") String query, Pageable pageable);

    /**
     * Searches contacts by organization with a case-insensitive query and cursor,
     * ordered by ID.
     *
     * @param orgId    the organization ID
     * @param query    the search term
     * @param cursor   the cursor ID (exclusive lower bound)
     * @param pageable pagination information
     * @return list of matching contact entities after the cursor, ordered by ID
     */
    @Query("SELECT c FROM ContactEntity c WHERE c.organizationId = :orgId "
            + "AND c.id > :cursor "
            + "AND (LOWER(c.formattedName) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(c.givenName) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(c.familyName) LIKE LOWER(CONCAT('%', :q, '%')))"
            + " ORDER BY c.id")
    List<ContactEntity> searchByOrganizationIdAndIdGreaterThanOrderById(
            @Param("orgId") UUID orgId, @Param("q") String query,
            @Param("cursor") UUID cursor, Pageable pageable);
}
