package com.majordomo.domain.port.in.steward;

import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.steward.Property;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Inbound port for managing properties in the Steward domain.
 */
public interface ManagePropertyUseCase {

    /**
     * Creates a new property, assigning a default status if none is set.
     *
     * @param property the property to create
     * @return the created property with generated ID, default status, and timestamps
     */
    Property create(Property property);

    /**
     * Finds a property by ID.
     *
     * @param id the property ID
     * @return the property, or empty if not found
     */
    Optional<Property> findById(UUID id);

    /**
     * Lists all properties for an organization.
     *
     * @param organizationId the organization ID
     * @return list of properties
     */
    List<Property> findByOrganizationId(UUID organizationId);

    /**
     * Lists properties for an organization with cursor-based pagination.
     *
     * @param organizationId the organization ID
     * @param cursor         the cursor UUID (null for first page)
     * @param limit          max results per page (1-100)
     * @return a page of properties
     */
    Page<Property> findByOrganizationId(UUID organizationId, UUID cursor, int limit);

    /**
     * Lists all direct child properties of the specified parent.
     *
     * @param parentId the parent property ID
     * @return list of child properties
     */
    List<Property> findByParentId(UUID parentId);

    /**
     * Updates an existing property.
     *
     * @param id       the property ID
     * @param property the updated property data
     * @return the updated property
     */
    Property update(UUID id, Property property);

    /**
     * Archives a property by setting archived_at.
     *
     * @param id the property ID
     */
    void archive(UUID id);
}
