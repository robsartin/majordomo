package com.majordomo.adapter.out.persistence.steward;

import com.majordomo.domain.model.steward.PropertyContact;
import com.majordomo.domain.port.out.steward.PropertyContactRepository;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PropertyContactRepositoryAdapter implements PropertyContactRepository {

    private final JpaPropertyContactRepository jpa;

    public PropertyContactRepositoryAdapter(JpaPropertyContactRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public PropertyContact save(PropertyContact propertyContact) {
        var entity = PropertyContactMapper.toEntity(propertyContact);
        return PropertyContactMapper.toDomain(jpa.save(entity));
    }

    @Override
    public Optional<PropertyContact> findById(UUID id) {
        return jpa.findById(id).map(PropertyContactMapper::toDomain);
    }

    @Override
    public List<PropertyContact> findByPropertyId(UUID propertyId) {
        return jpa.findByPropertyId(propertyId).stream().map(PropertyContactMapper::toDomain).toList();
    }

    @Override
    public List<PropertyContact> findByContactId(UUID contactId) {
        return jpa.findByContactId(contactId).stream().map(PropertyContactMapper::toDomain).toList();
    }
}
