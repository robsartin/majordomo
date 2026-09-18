package com.majordomo.domain.port.out.librarian;

import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.librarian.Book;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for persisting and querying books.
 */
public interface BookRepository {

    /**
     * Persists a book, inserting or updating as needed.
     *
     * @param book the book to save
     * @return the saved book, including any generated or updated fields
     */
    Book save(Book book);

    /**
     * Retrieves a book by its unique identifier.
     *
     * @param id the book ID
     * @return the book, or empty if not found
     */
    Optional<Book> findById(UUID id);

    /**
     * Finds a book by the key import dedupes on: title and author, both
     * normalised. This is what makes a re-import an update rather than a
     * duplicate.
     *
     * @param normalizedKey  the normalised title-and-author key
     * @param organizationId owning org
     * @return the matching book, or empty if this is a new one
     */
    Optional<Book> findByNormalizedKey(String normalizedKey, UUID organizationId);

    /**
     * Lists non-archived books for an organization, newest first.
     *
     * @param organizationId owning org
     * @param cursor         the id to resume after, or null to start
     * @param limit          page size
     * @return a page of books
     */
    Page<Book> findByOrganization(UUID organizationId, UUID cursor, int limit);
}
