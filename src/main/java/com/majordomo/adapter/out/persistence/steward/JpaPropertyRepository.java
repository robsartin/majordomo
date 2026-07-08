package com.majordomo.adapter.out.persistence.steward;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
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
     * Active (non-archived) properties in an organization. Adapter uses this
     * when the link picker has no exclusions.
     *
     * @param organizationId organization scope
     * @return active property entities
     */
    List<PropertyEntity> findByOrganizationIdAndArchivedAtIsNull(UUID organizationId);

    /**
     * Active (non-archived) properties in an organization, excluding given
     * non-empty ids. Caller must check the excluded set is non-empty.
     *
     * @param organizationId organization scope
     * @param excludedIds    ids to omit (must be non-empty)
     * @return matching active property entities
     */
    List<PropertyEntity> findByOrganizationIdAndArchivedAtIsNullAndIdNotIn(
            UUID organizationId, Collection<UUID> excludedIds);

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

    /**
     * Non-archived properties in an organization whose warranty expires on or after
     * {@code from}, soonest first. Backs the warranty-expiration calendar feed.
     *
     * @param organizationId the organization scope
     * @param from           inclusive lower bound for {@code warrantyExpiresOn}
     * @return matching property entities, ordered by expiry date
     */
    @Query("SELECT p FROM PropertyEntity p WHERE p.organizationId = :organizationId "
            + "AND p.warrantyExpiresOn IS NOT NULL "
            + "AND p.warrantyExpiresOn >= :from "
            + "AND p.archivedAt IS NULL "
            + "ORDER BY p.warrantyExpiresOn")
    List<PropertyEntity> findWithWarrantyExpiringOnOrAfter(
            @Param("organizationId") UUID organizationId, @Param("from") LocalDate from);

    /**
     * Cursor-paginated full-text property search within an organization. When
     * {@code query} is null the text predicate is skipped (plain org listing with
     * optional filters); otherwise a match is any property whose generated
     * {@code search_vector} matches, or which has a non-archived attachment whose
     * filename matches, the {@code plainto_tsquery}. Results are ordered by id so
     * the {@code id > cursor} keyset pagination stays stable. Postgres-specific.
     *
     * @param organizationId required org scope
     * @param query          search text (null = no text filter)
     * @param category       optional exact category filter (null = any)
     * @param status         optional exact status filter (null = any)
     * @param cursor         exclusive keyset cursor (null = first page)
     * @param limit          row cap
     * @return matching property entities, ordered by id
     */
    @Query(value = """
            SELECT p.* FROM properties p
             WHERE p.organization_id = :organizationId
               AND (CAST(:cursor AS uuid) IS NULL OR p.id > CAST(:cursor AS uuid))
               AND (CAST(:category AS text) IS NULL OR p.category = CAST(:category AS text))
               AND (CAST(:status AS text) IS NULL OR p.status = CAST(:status AS text))
               AND (
                    CAST(:query AS text) IS NULL
                    OR p.search_vector @@ plainto_tsquery('english', CAST(:query AS text))
                    OR EXISTS (
                         SELECT 1 FROM attachments a
                          WHERE a.entity_type = 'PROPERTY'
                            AND a.entity_id = p.id
                            AND a.archived_at IS NULL
                            AND to_tsvector('english',
                                    regexp_replace(a.filename, '[^A-Za-z0-9]+', ' ', 'g'))
                                @@ plainto_tsquery('english', CAST(:query AS text))
                    )
               )
             ORDER BY p.id
             LIMIT :limit
            """, nativeQuery = true)
    List<PropertyEntity> searchFullText(
            @Param("organizationId") UUID organizationId,
            @Param("query") String query,
            @Param("category") String category,
            @Param("status") String status,
            @Param("cursor") UUID cursor,
            @Param("limit") int limit);
}
