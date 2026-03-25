package com.majordomo.adapter.out.persistence.steward;

import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.port.out.steward.PropertyRepository;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PropertyRepositoryAdapter implements PropertyRepository {

    private final JpaPropertyRepository jpa;

    public PropertyRepositoryAdapter(JpaPropertyRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Property save(Property property) {
        var entity = PropertyMapper.toEntity(property);
        return PropertyMapper.toDomain(jpa.save(entity));
    }

    @Override
    public Optional<Property> findById(UUID id) {
        return jpa.findById(id).map(PropertyMapper::toDomain);
    }

    @Override
    public List<Property> findByOrganizationId(UUID organizationId) {
        return jpa.findByOrganizationId(organizationId).stream().map(PropertyMapper::toDomain).toList();
    }

    @Override
    public List<Property> findByParentId(UUID parentId) {
        return jpa.findByParentId(parentId).stream().map(PropertyMapper::toDomain).toList();
    }
}
