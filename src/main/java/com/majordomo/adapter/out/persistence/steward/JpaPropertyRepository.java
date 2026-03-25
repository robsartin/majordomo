package com.majordomo.adapter.out.persistence.steward;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link PropertyEntity}, providing persistence operations
 * used by {@link PropertyRepositoryAdapter}.
 */
public interface JpaPropertyRepository extends JpaRepository<PropertyEntity, UUID> {

    List<PropertyEntity> findByOrganizationId(UUID organizationId);

    List<PropertyEntity> findByParentId(UUID parentId);
}
