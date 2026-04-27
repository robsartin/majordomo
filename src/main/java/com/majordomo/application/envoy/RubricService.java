package com.majordomo.application.envoy;

import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.Rubric;
import com.majordomo.domain.model.event.RubricVersionCreated;
import com.majordomo.domain.port.in.envoy.ManageRubricUseCase;
import com.majordomo.domain.port.out.EventPublisher;
import com.majordomo.domain.port.out.envoy.RubricRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Rubric authoring — every save produces a new monotonically-increasing version. */
@Service
public class RubricService implements ManageRubricUseCase {

    private final RubricRepository repo;
    private final EventPublisher eventPublisher;

    /**
     * Constructs the service.
     *
     * @param repo           outbound rubric repository
     * @param eventPublisher domain event publisher (fans out a {@link RubricVersionCreated}
     *                       to drive automatic re-scoring of postings in the org)
     */
    public RubricService(RubricRepository repo, EventPublisher eventPublisher) {
        this.repo = repo;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Rubric saveNewVersion(String name, Rubric submitted, UUID organizationId) {
        // Only an existing org-specific version increments — system-default
        // ({@code organizationId IS NULL}) is the seed and stays at v1.
        int nextVersion = repo.findActiveByName(name, organizationId)
                .filter(r -> r.organizationId().isPresent())
                .map(r -> r.version() + 1)
                .orElse(1);
        Rubric toSave = new Rubric(
                UuidFactory.newId(),
                Optional.of(organizationId),
                nextVersion,
                name,
                submitted.disqualifiers(),
                submitted.categories(),
                submitted.flags(),
                submitted.thresholds(),
                Instant.now());
        Rubric saved = repo.save(toSave);
        eventPublisher.publish(new RubricVersionCreated(
                organizationId, name, saved.version(), Instant.now()));
        return saved;
    }
}
