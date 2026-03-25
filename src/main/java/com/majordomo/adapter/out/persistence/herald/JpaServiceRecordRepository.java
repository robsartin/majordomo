package com.majordomo.adapter.out.persistence.herald;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ServiceRecordEntity}, providing persistence operations
 * used by {@link ServiceRecordRepositoryAdapter}.
 */
public interface JpaServiceRecordRepository extends JpaRepository<ServiceRecordEntity, UUID> {

    List<ServiceRecordEntity> findByPropertyId(UUID propertyId);

    List<ServiceRecordEntity> findByScheduleId(UUID scheduleId);
}
