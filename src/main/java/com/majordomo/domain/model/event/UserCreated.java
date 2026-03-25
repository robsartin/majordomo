package com.majordomo.domain.model.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a new user is created in an organization.
 *
 * @param userId         the ID of the newly created user
 * @param organizationId the organization the user was added to
 * @param username       the username of the new user
 * @param occurredAt     when the event occurred
 */
public record UserCreated(
    UUID userId,
    UUID organizationId,
    String username,
    Instant occurredAt
) { }
