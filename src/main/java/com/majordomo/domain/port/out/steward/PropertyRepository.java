package com.majordomo.domain.port.out.steward;

import com.majordomo.domain.model.steward.Property;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for persisting and querying properties.
 * Properties are the physical or logical assets managed by an organization.
 * They may be nested (e.g. a unit within a building), which is expressed via
 * the parent–child relationship.
 */
public interface PropertyRepository {

    /**
     * Persists a property, inserting or updating as needed.
     *
     * @param property the property to save
     * @return the saved property, including any generated or updated fields
     */
    Property save(Property property);

    /**
     * Retrieves a property by its unique identifier.
     *
     * @param id the property ID
     * @return the property, or empty if not found
     */
    Optional<Property> findById(UUID id);

    /**
     * Batch-fetches properties by id. Used by detail handlers that need to
     * hydrate a small set of linked properties in a single round trip.
     *
     * @param ids the property ids to load
     * @return the matching properties in arbitrary order, missing ids omitted
     */
    List<Property> findByIdIn(Collection<UUID> ids);

    /**
     * Returns all top-level and nested properties belonging to a given organization.
     *
     * @param organizationId the organization whose properties are sought
     * @return list of properties for that organization, or an empty list if none exist
     */
    List<Property> findByOrganizationId(UUID organizationId);

    /**
     * Returns active (non-archived) properties in an organization, excluding
     * the given ids. Used by link-picker dropdowns that need "every property
     * in the org except those already linked here".
     *
     * @param organizationId the organization scope
     * @param excludedIds    ids to omit from the result (may be empty)
     * @return list of matching active properties; empty if the org has none
     */
    List<Property> findActiveByOrganizationIdExcluding(UUID organizationId, Collection<UUID> excludedIds);

    /**
     * Returns properties for an organization with cursor-based pagination.
     *
     * @param organizationId the organization ID
     * @param cursor         exclusive start cursor (null for first page)
     * @param limit          maximum number of results
     * @return list of properties after the cursor, ordered by ID
     */
    List<Property> findByOrganizationId(UUID organizationId, UUID cursor, int limit);

    /**
     * Returns all direct child properties of a given parent property.
     *
     * @param parentId the ID of the parent property
     * @return list of child properties, or an empty list if the parent has none
     */
    List<Property> findByParentId(UUID parentId);

    /**
     * Searches properties for an organization by a case-insensitive query across
     * key text fields (name, description, location), with optional category and status
     * filters and cursor-based pagination.
     *
     * @param organizationId the organization whose properties are searched
     * @param query          the search term (matched via case-insensitive LIKE)
     * @param category       optional category filter (exact match, null to skip)
     * @param status         optional status filter (exact match, null to skip)
     * @param cursor         exclusive start cursor (null for first page)
     * @param limit          maximum number of results
     * @return list of matching properties after the cursor, ordered by ID
     */
    List<Property> search(UUID organizationId, String query, String category,
                          String status, UUID cursor, int limit);

    /**
     * Returns all properties whose warranty expires before the given date
     * and whose warranty notification has not yet been sent.
     *
     * @param date properties with {@code warrantyExpiresOn} before this date are returned
     * @return list of matching properties, or an empty list if none exist
     */
    List<Property> findWithWarrantyExpiringBefore(LocalDate date);
}
