package com.majordomo.domain.port.out.steward;

import com.majordomo.domain.model.steward.Property;

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
     * Returns all top-level and nested properties belonging to a given organization.
     *
     * @param organizationId the organization whose properties are sought
     * @return list of properties for that organization, or an empty list if none exist
     */
    List<Property> findByOrganizationId(UUID organizationId);

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
}
