package com.majordomo.adapter.in.web.steward;

import java.util.List;

/**
 * Result of a bulk property import operation.
 *
 * @param total   total rows processed
 * @param created number of properties created
 * @param skipped number of rows skipped
 * @param errors  list of row-level errors
 */
public record ImportResult(
    int total,
    int created,
    int skipped,
    List<ImportError> errors
) {
    /**
     * A single row-level error encountered during import.
     *
     * @param row     the 1-based row number
     * @param message the error description
     */
    public record ImportError(int row, String message) { }
}
