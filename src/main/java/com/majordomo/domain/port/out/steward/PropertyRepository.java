package com.majordomo.domain.port.out.steward;

import com.majordomo.domain.model.steward.Property;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PropertyRepository {

    Property save(Property property);

    Optional<Property> findById(UUID id);

    List<Property> findByOrganizationId(UUID organizationId);

    List<Property> findByParentId(UUID parentId);
}
