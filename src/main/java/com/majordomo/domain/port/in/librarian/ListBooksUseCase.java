package com.majordomo.domain.port.in.librarian;

import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.librarian.Book;
import com.majordomo.domain.model.librarian.BookFilter;

import java.util.UUID;

/**
 * Inbound port for reading the catalog.
 */
public interface ListBooksUseCase {

    /**
     * Lists non-archived books for an organization, oldest first by UUIDv7 id,
     * consistent with every other cursor-paginated query in the codebase.
     *
     * @param organizationId owning org
     * @param filter         optional narrowing criteria; never null
     * @param cursor         the id to resume after, or null to start
     * @param limit          page size
     * @return a page of books
     */
    Page<Book> list(UUID organizationId, BookFilter filter, UUID cursor, int limit);
}
