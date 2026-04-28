package com.majordomo.adapter.out.persistence.envoy;

import com.majordomo.domain.model.envoy.Rubric;
import com.majordomo.domain.port.out.envoy.RubricRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed adapter for {@link RubricRepository}. Resolves the active rubric
 * by preferring an org-specific version, then falling back to the seeded
 * system-default ({@code organization_id IS NULL}).
 */
@Repository
public class RubricRepositoryAdapter implements RubricRepository {

    private final JpaRubricRepository jpa;

    /**
     * Constructs the adapter.
     *
     * @param jpa Spring Data repository for {@link RubricEntity}
     */
    public RubricRepositoryAdapter(JpaRubricRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Rubric> findActiveByName(String name, UUID organizationId) {
        Optional<RubricEntity> orgSpecific =
                jpa.findFirstByOrganizationIdAndNameOrderByVersionDesc(organizationId, name);
        if (orgSpecific.isPresent()) {
            return orgSpecific.map(RubricMapper::toDomain);
        }
        return jpa.findFirstByOrganizationIdIsNullAndNameOrderByVersionDesc(name)
                .map(RubricMapper::toDomain);
    }

    @Override
    public Optional<Rubric> findById(UUID id) {
        return jpa.findById(id).map(RubricMapper::toDomain);
    }

    @Override
    public List<Rubric> findAllVersionsByName(String name, UUID organizationId) {
        return jpa.findAllByOrganizationIdAndNameOrderByVersionAsc(organizationId, name).stream()
                .map(RubricMapper::toDomain)
                .toList();
    }

    @Override
    public List<Rubric> findActiveRubricsForOrg(UUID organizationId) {
        // For each distinct name, prefer the org-specific highest-version row;
        // otherwise fall back to the system-default highest-version row.
        Map<String, RubricEntity> byName = new HashMap<>();
        // Org-specific rows (highest version first by ORDER BY in query).
        for (RubricEntity row : jpa.findAllByOrganizationIdOrderByNameAscVersionDesc(organizationId)) {
            byName.putIfAbsent(row.getName(), row);
        }
        // System-default rows fill in any names the org has not authored.
        for (RubricEntity row : jpa.findAllByOrganizationIdIsNullOrderByNameAscVersionDesc()) {
            byName.putIfAbsent(row.getName(), row);
        }
        List<Rubric> result = new ArrayList<>(byName.size());
        for (RubricEntity row : byName.values()) {
            result.add(RubricMapper.toDomain(row));
        }
        return result;
    }

    @Override
    public Optional<Rubric> findByOrgNameVersion(String name, int version, UUID organizationId) {
        Optional<RubricEntity> orgSpecific =
                jpa.findByOrganizationIdAndNameAndVersion(organizationId, name, version);
        if (orgSpecific.isPresent()) {
            return orgSpecific.map(RubricMapper::toDomain);
        }
        return jpa.findByOrganizationIdIsNullAndNameAndVersion(name, version)
                .map(RubricMapper::toDomain);
    }

    @Override
    public Rubric save(Rubric rubric) {
        return RubricMapper.toDomain(jpa.save(RubricMapper.toEntity(rubric)));
    }
}
