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
     * Returns all direct child properties of a given parent property.
     *
     * @param parentId the ID of the parent property
     * @return list of child properties, or an empty list if the parent has none
     */
    List<Property> findByParentId(UUID parentId);
}
