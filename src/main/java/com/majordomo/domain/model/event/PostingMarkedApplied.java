package com.majordomo.domain.model.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a job posting is marked as applied by the user.
 *
 * @param postingId      the posting that was marked applied
 * @param organizationId the owning organization
 * @param occurredAt     when the event occurred
 */
public record PostingMarkedApplied(
        UUID postingId,
        UUID organizationId,
        Instant occurredAt) { }
