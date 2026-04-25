package com.majordomo.application.envoy;

import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.Category;
import com.majordomo.domain.model.envoy.Rubric;
import com.majordomo.domain.model.envoy.Thresholds;
import com.majordomo.domain.model.envoy.Tier;
import com.majordomo.domain.port.out.envoy.RubricRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RubricServiceTest {

    private final UUID orgId = UuidFactory.newId();

    @Test
    void saveNewVersion_incrementsVersionAndStampsFields() {
        var repo = mock(RubricRepository.class);
        var existing = new Rubric(UuidFactory.newId(), Optional.of(orgId), 3, "default",
                List.of(), List.of(cat()), List.of(),
                new Thresholds(20, 15, 5), Instant.now().minusSeconds(3600));
        when(repo.findActiveByName("default", orgId)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var submitted = new Rubric(null, Optional.empty(), 0, "default", List.of(),
                List.of(cat()), List.of(), new Thresholds(30, 20, 10), null);

        var service = new RubricService(repo);
        Rubric saved = service.saveNewVersion("default", submitted, orgId);

        assertThat(saved.version()).isEqualTo(4);
        assertThat(saved.id()).isNotNull();
        assertThat(saved.effectiveFrom()).isNotNull();
        assertThat(saved.organizationId()).contains(orgId);
        assertThat(saved.thresholds().applyImmediately()).isEqualTo(30);
    }

    @Test
    void saveNewVersion_startsAtV1WhenNoOrgSpecificExists() {
        var repo = mock(RubricRepository.class);
        when(repo.findActiveByName("brand-new", orgId)).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var submitted = new Rubric(null, Optional.empty(), 0, "brand-new", List.of(),
                List.of(cat()), List.of(), new Thresholds(30, 20, 10), null);

        var saved = new RubricService(repo).saveNewVersion("brand-new", submitted, orgId);

        assertThat(saved.version()).isEqualTo(1);
        assertThat(saved.organizationId()).contains(orgId);
    }

    @Test
    void saveNewVersion_systemDefaultDoesNotIncrement() {
        // org has no override yet — findActiveByName falls back to the seeded
        // system-default (organizationId.isEmpty()). The new version starts at 1.
        var repo = mock(RubricRepository.class);
        var systemDefault = new Rubric(UuidFactory.newId(), Optional.empty(), 1, "default",
                List.of(), List.of(cat()), List.of(),
                new Thresholds(20, 15, 5), Instant.now());
        when(repo.findActiveByName("default", orgId)).thenReturn(Optional.of(systemDefault));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var submitted = new Rubric(null, Optional.empty(), 0, "default", List.of(),
                List.of(cat()), List.of(), new Thresholds(30, 20, 10), null);

        var saved = new RubricService(repo).saveNewVersion("default", submitted, orgId);

        assertThat(saved.version()).isEqualTo(1);
    }

    private Category cat() {
        return new Category("c", "x", 10, List.of(new Tier("Only", 5, "x")));
    }
}
