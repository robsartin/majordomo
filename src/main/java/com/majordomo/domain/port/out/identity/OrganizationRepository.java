package com.majordomo.domain.port.out.identity;

import com.majordomo.domain.model.identity.Organization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for persisting and querying organizations.
 * An organization is the top-level tenant boundary within the system; users
 * belong to one or more organizations and all domain resources are scoped beneath them.
 */
public interface OrganizationRepository {

    /**
     * Persists an organization, inserting or updating as needed.
     *
     * @param organization the organization to save
     * @return the saved organization, including any generated or updated fields
     */
    Organization save(Organization organization);

    /**
     * Retrieves an organization by its unique identifier.
     *
     * @param id the organization ID
     * @return the organization, or empty if not found
     */
    Optional<Organization> findById(UUID id);

    /**
     * Returns all organizations to which the given user belongs.
     *
     * @param userId the user whose organizations are sought
     * @return list of organizations for that user, or an empty list if none exist
     */
    List<Organization> findByUserId(UUID userId);
}
