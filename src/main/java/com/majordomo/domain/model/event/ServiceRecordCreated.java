package com.majordomo.domain.model.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a new service record is created for a property.
 *
 * @param serviceRecordId the ID of the newly created service record
 * @param propertyId      the property the service was performed on
 * @param scheduleId      the schedule that prompted the service (may be null)
 * @param occurredAt      when the event occurred
 */
public record ServiceRecordCreated(
    UUID serviceRecordId,
    UUID propertyId,
    UUID scheduleId,
    Instant occurredAt
) { }
