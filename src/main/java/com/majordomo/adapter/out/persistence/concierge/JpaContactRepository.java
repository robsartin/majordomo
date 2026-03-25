package com.majordomo.adapter.out.persistence.concierge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ContactEntity}, providing persistence operations
 * used by {@link ContactRepositoryAdapter}.
 */
public interface JpaContactRepository extends JpaRepository<ContactEntity, UUID> {

    List<ContactEntity> findByOrganizationId(UUID organizationId);
}
