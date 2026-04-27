package com.majordomo.domain.model.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published after a new rubric version is persisted by {@code RubricService}.
 * Triggers a fan-out re-score of every posting in the org against the new
 * version (see {@code RubricChangeRescoreListener}).
 *
 * @param organizationId the owning organization
 * @param rubricName     the rubric name (e.g. "default")
 * @param version        the newly-minted version number
 * @param occurredAt     when the event occurred
 */
public record RubricVersionCreated(
        UUID organizationId,
        String rubricName,
        int version,
        Instant occurredAt) { }
