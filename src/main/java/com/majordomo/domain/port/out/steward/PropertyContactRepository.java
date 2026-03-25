package com.majordomo.domain.port.out.steward;

import com.majordomo.domain.model.steward.PropertyContact;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PropertyContactRepository {

    PropertyContact save(PropertyContact propertyContact);

    Optional<PropertyContact> findById(UUID id);

    List<PropertyContact> findByPropertyId(UUID propertyId);

    List<PropertyContact> findByContactId(UUID contactId);
}
