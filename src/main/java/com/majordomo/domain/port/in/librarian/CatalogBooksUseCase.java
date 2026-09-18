package com.majordomo.domain.port.in.librarian;

import com.majordomo.domain.model.librarian.Book;
import com.majordomo.domain.model.librarian.BookImportRow;

import java.util.List;
import java.util.UUID;

/**
 * Inbound port for adding books to the catalog from transcribed rows.
 *
 * <p>Import upserts on the normalised title-and-author key rather than skipping
 * rows that already exist (ADR-0023). Until the web UI ships, the CSV is the
 * only way to record a rating, location or status, so an importer that skipped
 * known rows would silently discard every edit.
 */
public interface CatalogBooksUseCase {

    /**
     * Imports rows into the catalog, inserting new books and updating the
     * owner-authored fields of ones already present.
     *
     * @param rows           the transcribed rows to import
     * @param organizationId owning org
     * @return the resulting books, both inserted and updated
     */
    List<Book> catalog(List<BookImportRow> rows, UUID organizationId);
}
