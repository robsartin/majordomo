package com.majordomo.application.steward;

/**
 * Filter set for {@link PropertyQueryService#list}. Both fields are nullable;
 * {@code null} or blank means "no filter on that dimension".
 *
 * @param category exact-match category filter (case-insensitive); null/blank = any
 * @param q        free-text query matched against name + description (case-insensitive); null/blank = any
 */
public record PropertyFilters(String category, String q) {

    /** @return filters with no constraints. */
    public static PropertyFilters none() {
        return new PropertyFilters(null, null);
    }
}
