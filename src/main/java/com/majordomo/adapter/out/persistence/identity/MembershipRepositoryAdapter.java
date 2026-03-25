package com.majordomo.adapter.out.persistence.identity;

import com.majordomo.domain.model.identity.Membership;
import com.majordomo.domain.port.out.identity.MembershipRepository;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MembershipRepositoryAdapter implements MembershipRepository {

    private final JpaMembershipRepository jpa;

    public MembershipRepositoryAdapter(JpaMembershipRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Membership save(Membership membership) {
        var entity = MembershipMapper.toEntity(membership);
        return MembershipMapper.toDomain(jpa.save(entity));
    }

    @Override
    public Optional<Membership> findById(UUID id) {
        return jpa.findById(id).map(MembershipMapper::toDomain);
    }

    @Override
    public List<Membership> findByOrganizationId(UUID organizationId) {
        return jpa.findByOrganizationId(organizationId).stream().map(MembershipMapper::toDomain).toList();
    }

    @Override
    public List<Membership> findByUserId(UUID userId) {
        return jpa.findByUserId(userId).stream().map(MembershipMapper::toDomain).toList();
    }
}
