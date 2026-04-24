package com.majordomo.application.envoy;

import com.majordomo.adapter.out.ingest.JobSource;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.JobSourceRequest;
import com.majordomo.domain.port.in.envoy.IngestJobPostingUseCase;
import com.majordomo.domain.port.out.envoy.JobPostingRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Routes ingestion requests to the first {@link JobSource} whose
 * {@link JobSource#supports(JobSourceRequest)} returns true. Deduplicates by
 * ({@code source}, {@code externalId}); if a posting with the same pair already
 * exists in the organization, returns it instead of re-inserting.
 */
@Service
public class JobIngestionService implements IngestJobPostingUseCase {

    private final List<JobSource> sources;
    private final JobPostingRepository postings;

    /**
     * Constructs the service. Spring injects all {@link JobSource} beans into
     * {@code sources}.
     *
     * @param sources  the discovered ingestion sources
     * @param postings outbound port for posting persistence
     */
    public JobIngestionService(List<JobSource> sources, JobPostingRepository postings) {
        this.sources = sources;
        this.postings = postings;
    }

    @Override
    public JobPosting ingest(JobSourceRequest request, UUID organizationId) {
        JobSource source = sources.stream()
                .filter(s -> s.supports(request))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No JobSource supports request type: " + request.type()));

        JobPosting fetched = source.fetch(request);
        fetched.setOrganizationId(organizationId);
        fetched.setFetchedAt(Instant.now());

        if (fetched.getExternalId() != null) {
            var existing = postings.findBySourceAndExternalId(
                    fetched.getSource(), fetched.getExternalId(), organizationId);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        if (fetched.getId() == null) {
            fetched.setId(UuidFactory.newId());
        }
        return postings.save(fetched);
    }
}
