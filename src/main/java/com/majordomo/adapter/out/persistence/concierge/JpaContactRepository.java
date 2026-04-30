package com.majordomo.adapter.out.persistence.concierge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ContactEntity}, providing persistence operations
 * used by {@link ContactRepositoryAdapter}.
 */
public interface JpaContactRepository extends JpaRepository<ContactEntity, UUID>,
        JpaSpecificationExecutor<ContactEntity> {

    /**
     * Returns all contacts for an organization (unbounded, for internal use).
     *
     * @param organizationId the organization ID
     * @return list of all contact entities for the organization
     */
    List<ContactEntity> findByOrganizationId(UUID organizationId);

    /**
     * Active (non-archived) contacts in an organization. The adapter calls
     * this when no contacts need to be excluded.
     *
     * @param organizationId organization scope
     * @return matching active contacts
     */
    List<ContactEntity> findByOrganizationIdAndArchivedAtIsNull(UUID organizationId);

    /**
     * Active (non-archived) contacts in an organization, excluding the given
     * non-empty set of ids. Callers must check the excluded set is non-empty
     * — the underlying SQL {@code IN ()} clause would be malformed otherwise.
     *
     * @param organizationId organization scope
     * @param excludedIds    ids to omit (must be non-empty)
     * @return matching active contacts
     */
    List<ContactEntity> findByOrganizationIdAndArchivedAtIsNullAndIdNotIn(
            UUID organizationId, Collection<UUID> excludedIds);
}
