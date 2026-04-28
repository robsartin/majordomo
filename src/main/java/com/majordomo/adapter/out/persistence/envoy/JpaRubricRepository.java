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

    /**
     * All org-specific rows for {@code organizationId}, ordered by name ascending
     * then version descending. Used to pick the highest-version row per name.
     *
     * @param organizationId org scope
     * @return rows grouped by name with newest version first within each group
     */
    List<RubricEntity> findAllByOrganizationIdOrderByNameAscVersionDesc(UUID organizationId);

    /**
     * All system-default rows ({@code organization_id IS NULL}), ordered by name
     * ascending then version descending. Used to pick the highest-version
     * system-default row per name.
     *
     * @return system-default rows grouped by name with newest version first
     */
    List<RubricEntity> findAllByOrganizationIdIsNullOrderByNameAscVersionDesc();

    /**
     * Exact-match finder for an org-specific rubric version.
     *
     * @param organizationId org scope
     * @param name           rubric name
     * @param version        version number
     * @return the matching org-specific row, if any
     */
    Optional<RubricEntity> findByOrganizationIdAndNameAndVersion(
            UUID organizationId, String name, int version);

    /**
     * Exact-match finder for a system-default rubric version.
     *
     * @param name    rubric name
     * @param version version number
     * @return the matching system-default row, if any
     */
    Optional<RubricEntity> findByOrganizationIdIsNullAndNameAndVersion(String name, int version);
}
