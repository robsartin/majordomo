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
import java.util.Collection;
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
    public List<Property> findByIdIn(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return jpa.findAllById(ids).stream().map(PropertyMapper::toDomain).toList();
    }

    @Override
    public List<Property> findActiveByOrganizationIdExcluding(UUID organizationId,
                                                              Collection<UUID> excludedIds) {
        List<PropertyEntity> rows = (excludedIds == null || excludedIds.isEmpty())
                ? jpa.findByOrganizationIdAndArchivedAtIsNull(organizationId)
                : jpa.findByOrganizationIdAndArchivedAtIsNullAndIdNotIn(organizationId, excludedIds);
        return rows.stream().map(PropertyMapper::toDomain).toList();
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
    public List<Property> findWithWarrantyExpiringOnOrAfter(UUID organizationId, LocalDate from) {
        return jpa.findWithWarrantyExpiringOnOrAfter(organizationId, from).stream()
                .map(PropertyMapper::toDomain).toList();
    }

    @Override
    public List<Property> search(UUID organizationId, String query, String category,
                                 String status, UUID cursor, int limit) {
        // Validate status the same way the previous implementation did (throw on
        // an unknown value) while passing the raw string to the native query,
        // which compares it against the stored status column.
        var statusFilter = status != null ? PropertyStatus.valueOf(status).name() : null;
        var textFilter = (query == null || query.isBlank()) ? null : query;
        return jpa.searchFullText(organizationId, textFilter, category, statusFilter, cursor, limit)
                .stream().map(PropertyMapper::toDomain).toList();
    }
}
