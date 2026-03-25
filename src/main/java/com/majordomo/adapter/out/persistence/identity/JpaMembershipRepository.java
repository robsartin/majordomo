package com.majordomo.adapter.out.persistence.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link MembershipEntity}, providing persistence operations
 * used by {@link MembershipRepositoryAdapter}.
 */
public interface JpaMembershipRepository extends JpaRepository<MembershipEntity, UUID> {

    List<MembershipEntity> findByOrganizationId(UUID organizationId);

    List<MembershipEntity> findByUserId(UUID userId);
}
