package com.majordomo.domain.model.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when an external match is applied to a book, whether automatically
 * above the confidence threshold or by a reviewer accepting a candidate.
 *
 * @param bookId         the enriched book
 * @param organizationId the organization the book belongs to
 * @param source         which catalog the identifier came from, e.g. {@code OPEN_LIBRARY}
 * @param externalId     the identifier applied
 * @param occurredAt     when the event occurred
 */
public record BookEnriched(
    UUID bookId,
    UUID organizationId,
    String source,
    String externalId,
    Instant occurredAt
) { }
