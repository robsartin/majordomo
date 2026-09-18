package com.majordomo.domain.port.in.librarian;

import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.librarian.Book;

import java.util.UUID;

/**
 * Inbound port for reading the catalog.
 */
public interface ListBooksUseCase {

    /**
     * Lists non-archived books for an organization, newest first.
     *
     * @param organizationId owning org
     * @param cursor         the id to resume after, or null to start
     * @param limit          page size
     * @return a page of books
     */
    Page<Book> list(UUID organizationId, UUID cursor, int limit);
}
