package com.majordomo.domain.port.out.identity;

import com.majordomo.domain.model.identity.Membership;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for persisting and querying memberships.
 * A membership represents the relationship between a user and an organization,
 * typically carrying a role or set of permissions within that organization.
 */
public interface MembershipRepository {

    /**
     * Persists a membership, inserting or updating as needed.
     *
     * @param membership the membership to save
     * @return the saved membership, including any generated or updated fields
     */
    Membership save(Membership membership);

    /**
     * Retrieves a membership by its unique identifier.
     *
     * @param id the membership ID
     * @return the membership, or empty if not found
     */
    Optional<Membership> findById(UUID id);

    /**
     * Returns all memberships belonging to a given organization.
     *
     * @param organizationId the organization whose memberships are sought
     * @return list of memberships for that organization, or an empty list if none exist
     */
    List<Membership> findByOrganizationId(UUID organizationId);

    /**
     * Returns all memberships held by a given user across organizations.
     *
     * @param userId the user whose memberships are sought
     * @return list of memberships for that user, or an empty list if none exist
     */
    List<Membership> findByUserId(UUID userId);
}
