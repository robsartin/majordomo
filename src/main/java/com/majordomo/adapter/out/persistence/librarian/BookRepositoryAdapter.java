package com.majordomo.adapter.out.persistence.librarian;

import com.majordomo.adapter.out.persistence.CursorSpecifications;
import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.librarian.Book;
import com.majordomo.domain.model.librarian.BookFilter;
import com.majordomo.domain.port.out.librarian.BookRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence adapter fulfilling the {@link BookRepository} output port by
 * delegating to {@link JpaBookRepository}.
 */
@Repository
public class BookRepositoryAdapter implements BookRepository {

    private final JpaBookRepository jpa;

    public BookRepositoryAdapter(JpaBookRepository jpa) {
        this.jpa = jpa;
    }

    /**
     * Case-insensitive substring over the title. Authors live in a Postgres
     * array, so they are matched by the same normalised key the importer
     * already stores rather than by unnesting the array in a Specification.
     */
    private Specification<BookEntity> titleOrAuthorContains(String query) {
        if (query == null) {
            return (root, q, cb) -> cb.conjunction();
        }
        String pattern = "%" + query.toLowerCase(java.util.Locale.ROOT) + "%";
        return (root, q, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), pattern),
                cb.like(cb.lower(root.get("normalizedKey")), pattern));
    }

    @Override
    public Book save(Book book) {
        return BookMapper.toDomain(jpa.save(BookMapper.toEntity(book)));
    }

    @Override
    public Optional<Book> findById(UUID id) {
        return jpa.findById(id).map(BookMapper::toDomain);
    }

    @Override
    public Optional<Book> findByNormalizedKey(String normalizedKey, UUID organizationId) {
        return jpa.findByNormalizedKeyAndOrganizationIdAndArchivedAtIsNull(normalizedKey, organizationId)
                .map(BookMapper::toDomain);
    }

    @Override
    public Page<Book> findByOrganization(UUID organizationId, BookFilter filter, UUID cursor, int limit) {
        int clamped = Math.max(1, Math.min(limit, 100));
        BookFilter criteria = filter == null ? BookFilter.none() : filter;
        Specification<BookEntity> spec = CursorSpecifications.<BookEntity>afterCursor(cursor)
                .and(CursorSpecifications.fieldEquals("organizationId", organizationId))
                .and((root, query, cb) -> cb.isNull(root.get("archivedAt")))
                .and(CursorSpecifications.fieldEquals("status", criteria.status()))
                .and(CursorSpecifications.fieldEquals("confidence", criteria.confidence()))
                .and(titleOrAuthorContains(criteria.query()));
        var rows = jpa.findAll(spec, PageRequest.of(0, clamped + 1, Sort.by("id")));
        var items = rows.getContent().stream().map(BookMapper::toDomain).toList();
        return Page.fromOverfetch(items, clamped, Book::getId);
    }
}
