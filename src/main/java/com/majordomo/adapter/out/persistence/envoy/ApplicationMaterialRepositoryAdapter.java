package com.majordomo.adapter.out.persistence.envoy;

import com.majordomo.domain.model.envoy.ApplicationMaterial;
import com.majordomo.domain.port.out.envoy.ApplicationMaterialRepository;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence adapter for generated application materials (#348).
 */
@Repository
public class ApplicationMaterialRepositoryAdapter implements ApplicationMaterialRepository {

    private final JpaApplicationMaterialRepository jpa;

    /**
     * Constructs the adapter.
     *
     * @param jpa the Spring Data repository
     */
    public ApplicationMaterialRepositoryAdapter(JpaApplicationMaterialRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public ApplicationMaterial save(ApplicationMaterial material) {
        jpa.save(ApplicationMaterialMapper.toEntity(material));
        return material;
    }

    @Override
    public Optional<ApplicationMaterial> findById(UUID id, UUID organizationId) {
        return jpa.findByIdAndOrganizationId(id, organizationId)
                .map(ApplicationMaterialMapper::toDomain);
    }

    @Override
    public List<ApplicationMaterial> findByPosting(UUID postingId, UUID organizationId) {
        return jpa.findByPostingIdAndOrganizationIdOrderByGeneratedAtDesc(postingId, organizationId)
                .stream()
                .map(ApplicationMaterialMapper::toDomain)
                .toList();
    }
}
