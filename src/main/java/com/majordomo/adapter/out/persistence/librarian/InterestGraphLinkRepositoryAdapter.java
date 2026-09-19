package com.majordomo.adapter.out.persistence.librarian;

import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.port.out.librarian.InterestGraphLinkRepository;

import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistence adapter for the interest-graph sync ledger.
 */
@Repository
public class InterestGraphLinkRepositoryAdapter implements InterestGraphLinkRepository {

    private final JpaInterestGraphLinkRepository jpa;

    /**
     * Constructs the adapter.
     *
     * @param jpa the Spring Data repository
     */
    public InterestGraphLinkRepositoryAdapter(JpaInterestGraphLinkRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public boolean record(UUID bookId, String qid) {
        if (jpa.existsByBookIdAndWikidataQid(bookId, qid)) {
            return false;
        }
        var entity = new InterestGraphLinkEntity();
        entity.setId(UuidFactory.newId());
        entity.setBookId(bookId);
        entity.setWikidataQid(qid);
        entity.setSyncedAt(Instant.now());
        jpa.save(entity);
        return true;
    }

    @Override
    public boolean exists(UUID bookId, String qid) {
        return jpa.existsByBookIdAndWikidataQid(bookId, qid);
    }
}
