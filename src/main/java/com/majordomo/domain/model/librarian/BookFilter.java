package com.majordomo.domain.model.librarian;

/**
 * Optional criteria for narrowing a catalog listing.
 *
 * <p>Filtering by {@link Confidence} is the one that earns its keep: it is how
 * the owner finds the rows a lossy transcription left doubtful, without walking
 * the whole shelf.
 *
 * @param status     restrict to one shelf status, or null for any
 * @param confidence restrict to one confidence grade, or null for any
 * @param query      case-insensitive substring of title or author, or null
 */
public record BookFilter(BookStatus status, Confidence confidence, String query) {

    private static final BookFilter NONE = new BookFilter(null, null, null);

    /**
     * Normalises a blank query to null, so an empty search box means "no filter"
     * rather than a substring that matches every row.
     *
     * @param status     the status criterion
     * @param confidence the confidence criterion
     * @param query      the free-text criterion
     */
    public BookFilter {
        query = (query == null || query.isBlank()) ? null : query.trim();
    }

    /**
     * The empty filter.
     *
     * @return a filter with no criteria
     */
    public static BookFilter none() {
        return NONE;
    }

    /**
     * Whether this filter narrows anything.
     *
     * @return true when no criterion is set
     */
    public boolean isEmpty() {
        return status == null && confidence == null && query == null;
    }
}
