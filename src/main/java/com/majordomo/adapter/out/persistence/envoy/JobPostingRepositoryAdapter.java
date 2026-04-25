package com.majordomo.adapter.out.persistence.envoy;

import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.port.out.envoy.JobPostingRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** JPA-backed adapter for {@link JobPostingRepository}. */
@Repository
public class JobPostingRepositoryAdapter implements JobPostingRepository {

    private final JpaJobPostingRepository jpa;

    /**
     * Constructs the adapter.
     *
     * @param jpa Spring Data repository for {@link JobPostingEntity}
     */
    public JobPostingRepositoryAdapter(JpaJobPostingRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public JobPosting save(JobPosting posting) {
        return JobPostingMapper.toDomain(jpa.save(JobPostingMapper.toEntity(posting)));
    }

    @Override
    public Optional<JobPosting> findById(UUID id, UUID organizationId) {
        return jpa.findByIdAndOrganizationId(id, organizationId)
                .map(JobPostingMapper::toDomain);
    }

    @Override
    public Optional<JobPosting> findBySourceAndExternalId(
            String source, String externalId, UUID organizationId) {
        return jpa.findBySourceAndExternalIdAndOrganizationId(source, externalId, organizationId)
                .map(JobPostingMapper::toDomain);
    }
}
