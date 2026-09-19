package com.majordomo.application.librarian;

import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.librarian.Book;
import com.majordomo.domain.model.librarian.BookStatus;
import com.majordomo.domain.model.librarian.EnrichmentCandidate;
import com.majordomo.domain.port.out.librarian.BookRepository;
import com.majordomo.domain.port.out.librarian.EnrichmentCandidateRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** In-memory doubles with real behaviour, shared by the enrichment tests. */
final class LibrarianEnrichmentTestSupport {

    private LibrarianEnrichmentTestSupport() {
    }

    static Book book(UUID orgId, String title, String... authors) {
        var b = new Book();
        b.setId(UuidFactory.newId());
        b.setOrganizationId(orgId);
        b.setTitle(title);
        b.setAuthors(List.of(authors));
        b.setStatus(BookStatus.OWNED);
        return b;
    }

    static final class FakeBooks implements BookRepository {
        final Map<UUID, Book> byId = new LinkedHashMap<>();

        @Override
        public Book save(Book book) {
            byId.put(book.getId(), book);
            return book;
        }

        @Override
        public Optional<Book> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<Book> findByNormalizedKey(String key, UUID orgId) {
            return Optional.empty();
        }

        @Override
        public Page<Book> findByOrganization(UUID orgId, UUID cursor, int limit) {
            return new Page<>(List.copyOf(byId.values()), null, false);
        }
    }

    static final class FakeCandidates implements EnrichmentCandidateRepository {
        final Map<UUID, EnrichmentCandidate> byId = new LinkedHashMap<>();

        @Override
        public EnrichmentCandidate save(EnrichmentCandidate c) {
            byId.put(c.id(), c);
            return c;
        }

        @Override
        public Optional<EnrichmentCandidate> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public List<EnrichmentCandidate> findByBookId(UUID bookId) {
            return byId.values().stream().filter(c -> c.bookId().equals(bookId)).toList();
        }

        @Override
        public Page<EnrichmentCandidate> findPending(UUID orgId, UUID cursor, int limit) {
            return new Page<>(byId.values().stream().filter(EnrichmentCandidate::isPending).toList(),
                    null, false);
        }
    }
}
