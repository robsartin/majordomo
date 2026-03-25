package com.majordomo.domain.model;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

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

    /**
     * Creates a page from a list that was fetched with limit+1 to detect hasMore.
     * Clamps the limit to [1, 100], trims the extra item if present, and extracts
     * the cursor from the last item in the page.
     *
     * @param items       the fetched items (may contain one extra for hasMore detection)
     * @param limit       the requested page size (will be clamped to [1, 100])
     * @param idExtractor function to extract the UUID from an item for cursor use
     * @param <T>         the item type
     * @return a page with correct items, cursor, and hasMore flag
     */
    public static <T> Page<T> fromOverfetch(List<T> items, int limit,
            Function<T, UUID> idExtractor) {
        int clampedLimit = Math.max(1, Math.min(limit, 100));
        boolean hasMore = items.size() > clampedLimit;
        List<T> pageItems = hasMore ? items.subList(0, clampedLimit) : items;
        UUID nextCursor = hasMore
                ? idExtractor.apply(pageItems.get(pageItems.size() - 1))
                : null;
        return new Page<>(pageItems, nextCursor, hasMore);
    }
}
