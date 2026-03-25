package com.majordomo.adapter.out.persistence.steward;

import com.majordomo.adapter.out.persistence.CursorSpecifications;
import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.model.steward.PropertyStatus;
import com.majordomo.domain.port.out.steward.PropertyRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
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
        var spec = Specification.where(
                        CursorSpecifications.<PropertyEntity>fieldEquals("organizationId", organizationId))
                .and(CursorSpecifications.afterCursor(cursor));
        var page = jpa.findAll(spec, PageRequest.of(0, limit, Sort.by("id")));
        return page.stream().map(PropertyMapper::toDomain).toList();
    }

    @Override
    public List<Property> findByParentId(UUID parentId) {
        return jpa.findByParentId(parentId).stream().map(PropertyMapper::toDomain).toList();
    }

    @Override
    public List<Property> findWithWarrantyExpiringBefore(LocalDate date) {
        return jpa.findWithWarrantyExpiringBefore(date).stream().map(PropertyMapper::toDomain).toList();
    }

    @Override
    public List<Property> search(UUID organizationId, String query, String category,
                                 String status, UUID cursor, int limit) {
        var statusEnum = status != null ? PropertyStatus.valueOf(status) : null;
        var spec = Specification.where(
                        CursorSpecifications.<PropertyEntity>fieldEquals("organizationId", organizationId))
                .and(CursorSpecifications.afterCursor(cursor))
                .and(CursorSpecifications.searchAcrossFields(query, "name", "description", "location"))
                .and(CursorSpecifications.fieldEquals("category", category))
                .and(CursorSpecifications.fieldEquals("status", statusEnum));
        var page = jpa.findAll(spec, PageRequest.of(0, limit, Sort.by("id")));
        return page.stream().map(PropertyMapper::toDomain).toList();
    }
}
