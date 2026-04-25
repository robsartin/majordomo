package com.majordomo.domain.port.out.envoy;

import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.JobSourceRequest;

/**
 * A pluggable ingestion source. Implementations are Spring-discovered and routed
 * by {@code JobIngestionService} — no central registry.
 */
public interface JobSource {

    /**
     * Stable name (e.g. {@code "manual"}, {@code "url"}, {@code "greenhouse"}).
     *
     * @return the source name
     */
    String name();

    /**
     * Whether this source can handle the given request.
     *
     * @param request the ingestion request
     * @return true iff this source supports the request
     */
    boolean supports(JobSourceRequest request);

    /**
     * Fetches the posting for {@code request}. Returns a posting with its
     * {@code source}, {@code rawText}, and {@code fetchedAt} populated at minimum.
     *
     * @param request the ingestion request
     * @return the fetched posting (not yet persisted)
     */
    JobPosting fetch(JobSourceRequest request);
}
