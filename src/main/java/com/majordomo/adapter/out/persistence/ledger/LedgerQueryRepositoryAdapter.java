package com.majordomo.adapter.out.persistence.ledger;

import com.majordomo.domain.port.out.ledger.LedgerQueryRepository;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Persistence adapter for ledger queries using JPQL aggregations.
 */
@Repository
public class LedgerQueryRepositoryAdapter implements LedgerQueryRepository {

    private final EntityManager entityManager;

    /**
     * Constructs the adapter with the JPA entity manager.
     *
     * @param entityManager the entity manager for executing queries
     */
    public LedgerQueryRepositoryAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public BigDecimal totalMaintenanceCost(UUID propertyId) {
        var result = entityManager.createQuery(
                "SELECT COALESCE(SUM(sr.cost), 0) FROM ServiceRecordEntity sr"
                    + " WHERE sr.propertyId = :propertyId",
                BigDecimal.class)
            .setParameter("propertyId", propertyId)
            .getSingleResult();
        return result;
    }

    @Override
    public BigDecimal totalMaintenanceCostByOrganization(UUID organizationId) {
        var result = entityManager.createQuery(
                "SELECT COALESCE(SUM(sr.cost), 0) FROM ServiceRecordEntity sr"
                    + " JOIN PropertyEntity p ON sr.propertyId = p.id"
                    + " WHERE p.organizationId = :organizationId",
                BigDecimal.class)
            .setParameter("organizationId", organizationId)
            .getSingleResult();
        return result;
    }
}
