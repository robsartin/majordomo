package com.majordomo.adapter.out.persistence.envoy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link RubricEntity}. */
public interface JpaRubricRepository extends JpaRepository<RubricEntity, UUID> {

    /**
     * Highest-version row for a given org and name.
     *
     * @param organizationId org scope
     * @param name           rubric name
     * @return latest org-specific version, if any
     */
    Optional<RubricEntity> findFirstByOrganizationIdAndNameOrderByVersionDesc(
            UUID organizationId, String name);

    /**
     * Highest-version system-default row (organization_id IS NULL) for a given name.
     *
     * @param name rubric name
     * @return latest system-default version, if any
     */
    Optional<RubricEntity> findFirstByOrganizationIdIsNullAndNameOrderByVersionDesc(String name);

    /**
     * All rows for an org and name, ordered by version ascending.
     *
     * @param organizationId org scope
     * @param name           rubric name
     * @return all org-specific versions, ascending
     */
    List<RubricEntity> findAllByOrganizationIdAndNameOrderByVersionAsc(
            UUID organizationId, String name);
}
