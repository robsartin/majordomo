package com.majordomo.adapter.out.persistence.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface JpaOrganizationRepository extends JpaRepository<OrganizationEntity, UUID> {

    @Query("SELECT o FROM OrganizationEntity o JOIN MembershipEntity m"
            + " ON o.id = m.organizationId WHERE m.userId = :userId")
    List<OrganizationEntity> findByUserId(UUID userId);
}
