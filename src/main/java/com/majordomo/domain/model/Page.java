package com.majordomo.domain.model;

import java.util.List;
import java.util.UUID;

/**
 * A page of results for cursor-based pagination.
 * Uses UUIDv7 as the cursor since IDs are time-sortable.
 *
 * @param <T>        the type of items in the page
 * @param items      the items in this page
 * @param nextCursor the ID to use as cursor for the next page, or null if no more
 * @param hasMore    true if there are more items after this page
 */
public record Page<T>(
    List<T> items,
    UUID nextCursor,
    boolean hasMore
) {
}
