package com.majordomo.adapter.out.persistence.envoy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ApplicationMaterialEntity}.
 */
public interface JpaApplicationMaterialRepository
        extends JpaRepository<ApplicationMaterialEntity, UUID> {

    Optional<ApplicationMaterialEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<ApplicationMaterialEntity> findByPostingIdAndOrganizationIdOrderByGeneratedAtDesc(
            UUID postingId, UUID organizationId);
}
