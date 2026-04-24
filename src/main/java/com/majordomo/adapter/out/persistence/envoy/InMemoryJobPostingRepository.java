package com.majordomo.adapter.out.persistence.envoy;

import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.port.out.envoy.JobPostingRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link JobPostingRepository} for the Phase-1 vertical slice.
 * Deleted in Phase 2.
 */
@Repository
@Profile("envoy-memory")
public class InMemoryJobPostingRepository implements JobPostingRepository {

    private final Map<UUID, JobPosting> byId = new ConcurrentHashMap<>();

    @Override
    public JobPosting save(JobPosting posting) {
        byId.put(posting.getId(), posting);
        return posting;
    }

    @Override
    public Optional<JobPosting> findById(UUID id, UUID organizationId) {
        return Optional.ofNullable(byId.get(id))
                .filter(p -> Objects.equals(p.getOrganizationId(), organizationId));
    }

    @Override
    public Optional<JobPosting> findBySourceAndExternalId(String source, String externalId, UUID organizationId) {
        return byId.values().stream()
                .filter(p -> Objects.equals(p.getOrganizationId(), organizationId))
                .filter(p -> Objects.equals(p.getSource(), source)
                        && Objects.equals(p.getExternalId(), externalId))
                .findFirst();
    }
}
