package com.majordomo.adapter.out.persistence.librarian;

import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.librarian.EnrichmentCandidate;
import com.majordomo.domain.port.out.librarian.EnrichmentCandidateRepository;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence adapter fulfilling the {@link EnrichmentCandidateRepository}
 * output port by delegating to {@link JpaEnrichmentCandidateRepository}.
 */
@Repository
public class EnrichmentCandidateRepositoryAdapter implements EnrichmentCandidateRepository {

    private final JpaEnrichmentCandidateRepository jpa;

    public EnrichmentCandidateRepositoryAdapter(JpaEnrichmentCandidateRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public EnrichmentCandidate save(EnrichmentCandidate candidate) {
        return EnrichmentCandidateMapper.toDomain(
                jpa.save(EnrichmentCandidateMapper.toEntity(candidate)));
    }

    @Override
    public Optional<EnrichmentCandidate> findById(UUID id) {
        return jpa.findById(id).map(EnrichmentCandidateMapper::toDomain);
    }

    @Override
    public List<EnrichmentCandidate> findByBookId(UUID bookId) {
        return jpa.findByBookIdOrderByScoreDesc(bookId).stream()
                .map(EnrichmentCandidateMapper::toDomain)
                .toList();
    }

    @Override
    public Page<EnrichmentCandidate> findPending(UUID organizationId, UUID cursor, int limit) {
        int clamped = Math.max(1, Math.min(limit, 100));
        var items = jpa.findPending(organizationId, cursor, Limit.of(clamped + 1)).stream()
                .map(EnrichmentCandidateMapper::toDomain)
                .toList();
        return Page.fromOverfetch(items, clamped, EnrichmentCandidate::id);
    }
}
