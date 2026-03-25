package com.majordomo.adapter.out.persistence.steward;

import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.model.steward.PropertyStatus;
import com.majordomo.domain.port.out.steward.PropertyRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence adapter that fulfills the {@link com.majordomo.domain.port.out.steward.PropertyRepository}
 * output port by delegating to {@link JpaPropertyRepository}.
 */
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
    public List<Property> findByOrganizationId(UUID organizationId, UUID cursor, int limit) {
        List<PropertyEntity> entities;
        if (cursor == null) {
            entities = jpa.findByOrganizationIdOrderById(organizationId, PageRequest.of(0, limit));
        } else {
            entities = jpa.findByOrganizationIdAndIdGreaterThanOrderById(
                    organizationId, cursor, PageRequest.of(0, limit));
        }
        return entities.stream().map(PropertyMapper::toDomain).toList();
    }

    @Override
    public List<Property> findByParentId(UUID parentId) {
        return jpa.findByParentId(parentId).stream().map(PropertyMapper::toDomain).toList();
    }

    @Override
    public List<Property> search(UUID organizationId, String query, String category,
                                 String status, UUID cursor, int limit) {
        var statusEnum = status != null ? PropertyStatus.valueOf(status) : null;
        List<PropertyEntity> entities;
        if (cursor == null) {
            entities = jpa.searchByOrganizationIdOrderById(
                    organizationId, query, category, statusEnum, PageRequest.of(0, limit));
        } else {
            entities = jpa.searchByOrganizationIdAndIdGreaterThanOrderById(
                    organizationId, query, category, statusEnum, cursor, PageRequest.of(0, limit));
        }
        return entities.stream().map(PropertyMapper::toDomain).toList();
    }
}
