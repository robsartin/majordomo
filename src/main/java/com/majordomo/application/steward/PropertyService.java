package com.majordomo.application.steward;

import com.majordomo.domain.model.steward.Property;
import com.majordomo.domain.model.steward.PropertyStatus;
import com.majordomo.domain.port.in.steward.ManagePropertyUseCase;
import com.majordomo.domain.port.out.steward.PropertyRepository;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service implementing property management use cases.
 * Bridges inbound ports to outbound repository ports.
 */
@Service
public class PropertyService implements ManagePropertyUseCase {

    private final PropertyRepository propertyRepository;

    /**
     * Constructs the service with the property repository port.
     *
     * @param propertyRepository the outbound port for property persistence
     */
    public PropertyService(PropertyRepository propertyRepository) {
        this.propertyRepository = propertyRepository;
    }

    @Override
    public Property create(Property property) {
        property.setId(UUID.randomUUID());
        if (property.getStatus() == null) {
            property.setStatus(PropertyStatus.ACTIVE);
        }
        property.setCreatedAt(Instant.now());
        property.setUpdatedAt(Instant.now());
        return propertyRepository.save(property);
    }

    @Override
    public Optional<Property> findById(UUID id) {
        return propertyRepository.findById(id);
    }

    @Override
    public List<Property> findByOrganizationId(UUID organizationId) {
        return propertyRepository.findByOrganizationId(organizationId);
    }

    @Override
    public List<Property> findByParentId(UUID parentId) {
        return propertyRepository.findByParentId(parentId);
    }
}
