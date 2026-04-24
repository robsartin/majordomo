package com.majordomo.adapter.out.persistence.envoy;

import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.Category;
import com.majordomo.domain.model.envoy.Disqualifier;
import com.majordomo.domain.model.envoy.Flag;
import com.majordomo.domain.model.envoy.Rubric;
import com.majordomo.domain.model.envoy.Thresholds;
import com.majordomo.domain.model.envoy.Tier;
import com.majordomo.domain.port.out.envoy.RubricRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link RubricRepository} used only in the Phase-1 vertical slice.
 * Seeds a hard-coded system-default "default" rubric on construction so the
 * integration test works without database setup. Deleted in Phase 2.
 */
@Repository
@Profile("envoy-memory")
public class InMemoryRubricRepository implements RubricRepository {

    private final Map<UUID, Rubric> byId = new ConcurrentHashMap<>();

    /**
     * Seeds the system-default "default" rubric.
     */
    public InMemoryRubricRepository() {
        var seed = new Rubric(
                UuidFactory.newId(), Optional.empty(), 1, "default",
                List.of(
                        new Disqualifier("ON_SITE_ONLY", "Role requires on-site work only"),
                        new Disqualifier("NON_ENGINEERING", "Posting is not an engineering role")),
                List.of(
                        new Category("compensation", "Base salary and equity", 25, List.of(
                                new Tier("Excellent", 25, "Base >$250k or total comp >$400k clearly stated"),
                                new Tier("Good", 18, "Base $200-250k or range suggesting it"),
                                new Tier("Fair", 10, "Base $150-200k"),
                                new Tier("Poor", 3, "Below $150k or no range given"))),
                        new Category("remote", "Remote flexibility", 15, List.of(
                                new Tier("Full remote", 15, "Fully remote, any US location"),
                                new Tier("Hybrid", 7, "Some in-office days required"),
                                new Tier("Regional remote", 3, "Remote but only from specific metros"))),
                        new Category("role_scope", "Seniority and scope match", 20, List.of(
                                new Tier("Strong match", 20, "Staff/Principal level, backend or platform"),
                                new Tier("Aligned", 12, "Senior engineer, clear backend work"),
                                new Tier("Weak", 4, "Junior or unclear scope"))),
                        new Category("team_signals", "Team/manager/culture signals", 15, List.of(
                                new Tier("Strong", 15, "Specific team, named manager, clear mandate"),
                                new Tier("Generic", 6, "Typical posting language"),
                                new Tier("Red", 1, "Mentions hustle culture, grind, 'rockstar' etc."))),
                        new Category("company_stage", "Company stability & stage", 15, List.of(
                                new Tier("Mature", 15, "Public or late-stage with revenue"),
                                new Tier("Growth", 9, "Series B/C/D with traction"),
                                new Tier("Early", 3, "Seed/Series A"),
                                new Tier("Risky", 0, "Pre-seed, no traction signals"))),
                        new Category("tech_stack", "Tech stack alignment", 10, List.of(
                                new Tier("Perfect", 10, "Java/Spring, Postgres, cloud"),
                                new Tier("Adjacent", 5, "Kotlin/Go/Python, same architecture space"),
                                new Tier("Misaligned", 1, "Frontend-heavy or ecosystem mismatch")))
                ),
                List.of(
                        new Flag("AT_WILL_EMPHASIS", "Unusually aggressive at-will language", 3),
                        new Flag("UNPAID_TEST", "Requires unpaid take-home >4 hours", 5),
                        new Flag("VAGUE_COMP", "No compensation range given at all", 2),
                        new Flag("ON_CALL_HEAVY", "Heavy on-call rotation explicitly required", 2)),
                new Thresholds(75, 55, 35),
                Instant.now());
        byId.put(seed.id(), seed);
    }

    @Override
    public Optional<Rubric> findActiveByName(String name, UUID organizationId) {
        Optional<Rubric> orgSpecific = byId.values().stream()
                .filter(r -> r.name().equals(name))
                .filter(r -> r.organizationId().filter(organizationId::equals).isPresent())
                .max(Comparator.comparingInt(Rubric::version));
        if (orgSpecific.isPresent()) {
            return orgSpecific;
        }
        return byId.values().stream()
                .filter(r -> r.name().equals(name))
                .filter(r -> r.organizationId().isEmpty())
                .max(Comparator.comparingInt(Rubric::version));
    }

    @Override
    public Optional<Rubric> findById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public List<Rubric> findAllVersionsByName(String name, UUID organizationId) {
        return byId.values().stream()
                .filter(r -> r.name().equals(name))
                .filter(r -> r.organizationId().filter(organizationId::equals).isPresent())
                .sorted(Comparator.comparingInt(Rubric::version))
                .toList();
    }

    @Override
    public Rubric save(Rubric rubric) {
        byId.put(rubric.id(), rubric);
        return rubric;
    }
}
