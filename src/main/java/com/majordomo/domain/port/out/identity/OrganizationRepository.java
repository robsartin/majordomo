package com.majordomo.domain.port.out.identity;

import com.majordomo.domain.model.identity.Organization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository {

    Organization save(Organization organization);

    Optional<Organization> findById(UUID id);

    List<Organization> findByUserId(UUID userId);
}
