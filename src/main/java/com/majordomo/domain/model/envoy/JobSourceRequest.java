package com.majordomo.domain.model.envoy;

import java.util.Map;

/**
 * A request to ingest a job posting. Routed by {@code JobIngestionService} to the
 * first {@code JobSource} whose {@code supports(...)} returns true.
 *
 * @param type    source discriminator (e.g. "manual", "url", "greenhouse")
 * @param payload the primary input — raw posting text, a URL, or a source-specific job ID
 * @param hints   optional out-of-band data (e.g. company name the caller already knows)
 */
public record JobSourceRequest(String type, String payload, Map<String, String> hints) { }
