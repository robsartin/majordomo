package com.majordomo.domain.model.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a book is first added to the catalog.
 *
 * <p>Carries {@code sourcePhoto} so downstream listeners can tell a row that
 * came from a shelf photograph from one entered by hand.
 *
 * @param bookId         the newly cataloged book
 * @param organizationId the organization the book belongs to
 * @param title          the title as cataloged
 * @param sourcePhoto    which shelf photograph the row came from, if any
 * @param occurredAt     when the event occurred
 */
public record BookCataloged(
    UUID bookId,
    UUID organizationId,
    String title,
    String sourcePhoto,
    Instant occurredAt
) { }
