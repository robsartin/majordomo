package com.majordomo.domain.model.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a property is archived (soft-deleted).
 *
 * @param propertyId     the ID of the archived property
 * @param organizationId the organization the property belongs to
 * @param occurredAt     when the event occurred
 */
public record PropertyArchived(
    UUID propertyId,
    UUID organizationId,
    Instant occurredAt
) { }
