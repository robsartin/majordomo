package com.majordomo.application.librarian;

import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.librarian.Book;
import com.majordomo.domain.model.librarian.BookFilter;
import com.majordomo.domain.port.in.librarian.ListBooksUseCase;
import com.majordomo.domain.port.out.librarian.BookRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Reads the catalog for the Librarian pages.
 */
@Service
public class BookQueryService implements ListBooksUseCase {

    private final BookRepository books;

    /**
     * Constructs the service.
     *
     * @param books the book repository
     */
    public BookQueryService(BookRepository books) {
        this.books = books;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Book> list(UUID organizationId, BookFilter filter, UUID cursor, int limit) {
        return books.findByOrganization(
                organizationId, filter == null ? BookFilter.none() : filter, cursor, limit);
    }
}
