package com.majordomo.domain.model.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a job posting is dismissed (not interested) by the user.
 *
 * @param postingId      the dismissed posting
 * @param organizationId the owning organization
 * @param occurredAt     when the event occurred
 */
public record PostingDismissed(
        UUID postingId,
        UUID organizationId,
        Instant occurredAt) { }
