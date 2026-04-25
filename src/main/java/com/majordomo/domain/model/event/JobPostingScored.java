package com.majordomo.domain.model.event;

import com.majordomo.domain.model.envoy.Recommendation;

import java.time.Instant;
import java.util.UUID;

/**
 * Published after a score report is persisted by the scoring service.
 *
 * @param reportId       the persisted report id
 * @param organizationId the owning organization
 * @param postingId      the posting that was scored
 * @param finalScore     the final score
 * @param recommendation the derived recommendation
 * @param occurredAt     when the event occurred
 */
public record JobPostingScored(
        UUID reportId,
        UUID organizationId,
        UUID postingId,
        int finalScore,
        Recommendation recommendation,
        Instant occurredAt) { }
