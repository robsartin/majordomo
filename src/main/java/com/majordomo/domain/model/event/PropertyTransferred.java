package com.majordomo.domain.model.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a property is transferred from one organization to another.
 *
 * @param propertyId the ID of the transferred property
 * @param fromOrgId  the source organization ID
 * @param toOrgId    the target organization ID
 * @param occurredAt when the transfer occurred
 */
public record PropertyTransferred(
    UUID propertyId,
    UUID fromOrgId,
    UUID toOrgId,
    Instant occurredAt
) { }
