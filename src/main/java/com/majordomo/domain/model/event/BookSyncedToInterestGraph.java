package com.majordomo.domain.model.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Published when a book's authors are pushed into the interest graph.
 *
 * <p>The rating rides along rather than gating the sync: every cataloged book
 * is sent, and the graph weights what it receives (ADR-0023). A {@code null}
 * rating means the owner has not rated the book, which is distinct from a low
 * one.
 *
 * @param bookId         the book that was synced
 * @param organizationId the organization the book belongs to
 * @param wikidataQid    the QID the sync joined on
 * @param authors        the author names pushed
 * @param rating         the owner's 1-5 rating, or {@code null} when unrated
 * @param occurredAt     when the event occurred
 */
public record BookSyncedToInterestGraph(
    UUID bookId,
    UUID organizationId,
    String wikidataQid,
    List<String> authors,
    Integer rating,
    Instant occurredAt
) { }
