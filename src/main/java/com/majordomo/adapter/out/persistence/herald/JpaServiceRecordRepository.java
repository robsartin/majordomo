package com.majordomo.adapter.out.persistence.herald;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ServiceRecordEntity}, providing persistence operations
 * used by {@link ServiceRecordRepositoryAdapter}.
 */
public interface JpaServiceRecordRepository extends JpaRepository<ServiceRecordEntity, UUID> {

    List<ServiceRecordEntity> findByPropertyId(UUID propertyId);

    List<ServiceRecordEntity> findByScheduleId(UUID scheduleId);

    /**
     * Returns recent service records for the given property IDs, ordered by performed date
     * descending.
     *
     * @param propertyIds the property IDs to filter on
     * @param pageable    pagination control (used to enforce limit)
     * @return matching entities ordered by {@code performedOn} descending
     */
    @Query("SELECT sr FROM ServiceRecordEntity sr WHERE sr.propertyId IN :propertyIds "
            + "ORDER BY sr.performedOn DESC")
    List<ServiceRecordEntity> findRecentByPropertyIds(
            @Param("propertyIds") List<UUID> propertyIds, Pageable pageable);
}
