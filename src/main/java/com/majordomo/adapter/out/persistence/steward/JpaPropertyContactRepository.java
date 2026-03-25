package com.majordomo.adapter.out.persistence.steward;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link PropertyContactEntity}, providing persistence operations
 * used by {@link PropertyContactRepositoryAdapter}.
 */
public interface JpaPropertyContactRepository extends JpaRepository<PropertyContactEntity, UUID> {

    List<PropertyContactEntity> findByPropertyId(UUID propertyId);

    List<PropertyContactEntity> findByContactId(UUID contactId);
}
