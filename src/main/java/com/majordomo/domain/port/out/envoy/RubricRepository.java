package com.majordomo.domain.port.out.envoy;

import com.majordomo.domain.model.envoy.Rubric;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for rubric persistence. Rubrics are append-only; "update" means
 * "insert a new version with {@code version + 1}".
 */
public interface RubricRepository {

    /**
     * Returns the active rubric for {@code (organizationId, name)}: the highest-version
     * rubric owned by {@code organizationId} if one exists, otherwise the highest-version
     * system-default rubric ({@code organization_id IS NULL}) for that name. Empty if
     * neither exists.
     *
     * @param name           logical rubric name
     * @param organizationId org scope for the lookup
     * @return the active rubric, or empty if none exists for this org or as system default
     */
    Optional<Rubric> findActiveByName(String name, UUID organizationId);

    /**
     * Finds a rubric by primary key (no org scoping — use for internal lookups only).
     *
     * @param id the rubric id
     * @return the rubric, or empty if not found
     */
    Optional<Rubric> findById(UUID id);

    /**
     * Lists all versions for {@code (organizationId, name)}, ordered by version ascending.
     * Does not include system-default versions.
     *
     * @param name           logical rubric name
     * @param organizationId org scope
     * @return all versions for this org+name, ascending by version
     */
    List<Rubric> findAllVersionsByName(String name, UUID organizationId);

    /**
     * Lists the active rubric for every distinct rubric name visible to
     * {@code organizationId}: the highest-version org-specific rubric per name,
     * plus the highest-version system-default rubric for any name where the org
     * has not authored its own version yet. Order is unspecified.
     *
     * @param organizationId org scope
     * @return one active rubric per distinct name visible to this org
     */
    List<Rubric> findActiveRubricsForOrg(UUID organizationId);

    /**
     * Persists a rubric. Caller is responsible for setting id, version, and effectiveFrom.
     *
     * @param rubric the rubric to persist
     * @return the persisted rubric
     */
    Rubric save(Rubric rubric);
}
