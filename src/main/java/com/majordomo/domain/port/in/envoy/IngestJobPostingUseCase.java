package com.majordomo.domain.port.in.envoy;

import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.JobSourceRequest;

import java.util.UUID;

/** Inbound port for ingesting a new posting from any {@code JobSource}. */
public interface IngestJobPostingUseCase {

    /**
     * Ingests a posting into the given organization. Routes {@code request} to the
     * first {@code JobSource} whose {@code supports(...)} returns true. Persists
     * and returns the posting with {@code organizationId} set.
     *
     * @param request        the source-typed ingestion request
     * @param organizationId the owning org
     * @return the persisted posting
     */
    JobPosting ingest(JobSourceRequest request, UUID organizationId);
}
