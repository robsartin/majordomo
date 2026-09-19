package com.majordomo.application.librarian;

import com.majordomo.domain.model.EntityNotFoundException;
import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.event.BookEnriched;
import com.majordomo.domain.model.librarian.Book;
import com.majordomo.domain.model.librarian.Confidence;
import com.majordomo.domain.model.librarian.EnrichmentCandidate;
import com.majordomo.domain.port.in.librarian.ReviewEnrichmentUseCase;
import com.majordomo.domain.port.out.EventPublisher;
import com.majordomo.domain.port.out.librarian.BookRepository;
import com.majordomo.domain.port.out.librarian.EnrichmentCandidateRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * The human gate between a lossy shelf transcription and the catalog.
 *
 * <p>A rejected candidate is decided, not deleted. Keeping it means a later
 * reviewer can see that a match was already considered and turned down, rather
 * than being offered the same wrong book again.
 */
@Service
public class EnrichmentReviewService implements ReviewEnrichmentUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(EnrichmentReviewService.class);

    private final BookRepository books;
    private final EnrichmentCandidateRepository candidates;
    private final EventPublisher events;

    /**
     * Constructs the service with required outbound ports.
     *
     * @param books      the book repository
     * @param candidates the enrichment candidate repository
     * @param events     the domain event publisher
     */
    public EnrichmentReviewService(BookRepository books,
                                   EnrichmentCandidateRepository candidates,
                                   EventPublisher events) {
        this.books = books;
        this.candidates = candidates;
        this.events = events;
    }

    @Override
    public Page<EnrichmentCandidate> pending(UUID organizationId, UUID cursor, int limit) {
        return candidates.findPending(organizationId, cursor, limit);
    }

    @Override
    @Transactional
    public void accept(UUID candidateId, UUID organizationId) {
        EnrichmentCandidate candidate = requirePending(candidateId, organizationId);
        Book book = requireBook(candidate.bookId(), organizationId);

        apply(book, candidate);
        books.save(book);
        candidates.save(candidate.withDecision(true, Instant.now()));
        events.publish(new BookEnriched(
                book.getId(), organizationId, candidate.source(), candidate.externalId(), Instant.now()));
    }

    @Override
    @Transactional
    public void reject(UUID candidateId, UUID organizationId) {
        EnrichmentCandidate candidate = requirePending(candidateId, organizationId);
        requireBook(candidate.bookId(), organizationId);
        candidates.save(candidate.withDecision(false, Instant.now()));
    }

    private EnrichmentCandidate requirePending(UUID candidateId, UUID organizationId) {
        EnrichmentCandidate candidate = candidates.findById(candidateId)
                .orElseThrow(() -> new EntityNotFoundException("ENRICHMENT_CANDIDATE", candidateId));
        // Scoping goes through the book: candidates carry no org of their own.
        requireBook(candidate.bookId(), organizationId);
        if (!candidate.isPending()) {
            throw new IllegalStateException(
                    "Enrichment candidate " + candidateId + " was already decided at " + candidate.reviewedAt());
        }
        return candidate;
    }

    private Book requireBook(UUID bookId, UUID organizationId) {
        return books.findById(bookId)
                .filter(b -> organizationId.equals(b.getOrganizationId()))
                .orElseThrow(() -> new EntityNotFoundException("BOOK", bookId));
    }

    private void apply(Book book, EnrichmentCandidate candidate) {
        Map<String, String> payload = candidate.payload();
        if (payload.get("isbn13") != null) {
            book.setIsbn13(payload.get("isbn13"));
        }
        if (payload.get("publisher") != null) {
            book.setPublisher(payload.get("publisher"));
        }
        if (payload.get("year") != null) {
            try {
                book.setYear(Integer.valueOf(payload.get("year")));
            } catch (NumberFormatException e) {
                LOG.warn("Ignoring unparseable year '{}' from {}", payload.get("year"), candidate.source());
            }
        }
        book.setOpenLibraryKey(candidate.externalId());
        book.setConfidence(Confidence.HIGH);
    }
}
