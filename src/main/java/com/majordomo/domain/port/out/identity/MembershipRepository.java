package com.majordomo.domain.port.out.identity;

import com.majordomo.domain.model.identity.Membership;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipRepository {

    Membership save(Membership membership);

    Optional<Membership> findById(UUID id);

    List<Membership> findByOrganizationId(UUID organizationId);

    List<Membership> findByUserId(UUID userId);
}
