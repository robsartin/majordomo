package com.majordomo.adapter.out.persistence.concierge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

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
}
