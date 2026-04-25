package com.majordomo.domain.model.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published after a new job posting is persisted by the ingestion service.
 *
 * @param postingId      the newly-persisted posting's id
 * @param organizationId the owning organization
 * @param source         the {@code JobSource.name()} that produced it
 * @param occurredAt     when the event occurred
 */
public record JobPostingIngested(
        UUID postingId,
        UUID organizationId,
        String source,
        Instant occurredAt) { }
