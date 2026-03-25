package com.majordomo.adapter.out.persistence.identity;

import com.majordomo.domain.model.identity.Organization;
import com.majordomo.domain.port.out.identity.OrganizationRepository;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence adapter that fulfills the {@link com.majordomo.domain.port.out.identity.OrganizationRepository}
 * output port by delegating to {@link JpaOrganizationRepository}.
 */
@Repository
public class OrganizationRepositoryAdapter implements OrganizationRepository {

    private final JpaOrganizationRepository jpa;

    public OrganizationRepositoryAdapter(JpaOrganizationRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Organization save(Organization organization) {
        var entity = OrganizationMapper.toEntity(organization);
        return OrganizationMapper.toDomain(jpa.save(entity));
    }

    @Override
    public Optional<Organization> findById(UUID id) {
        return jpa.findById(id).map(OrganizationMapper::toDomain);
    }

    @Override
    public List<Organization> findByUserId(UUID userId) {
        return jpa.findByUserId(userId).stream().map(OrganizationMapper::toDomain).toList();
    }
}
