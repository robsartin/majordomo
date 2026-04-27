package com.majordomo.adapter.out.persistence.envoy;

import com.majordomo.domain.model.UuidFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.ComponentScan;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice test for {@link JpaJobPostingRepository#findAllByOrganizationId(UUID)}
 * — confirms the new finder respects org boundaries.
 */
@DataJpaTest
@AutoConfigureTestDatabase
@ComponentScan(basePackageClasses = JpaJobPostingRepository.class)
class JpaJobPostingRepositoryTest {

    @Autowired
    private JpaJobPostingRepository repo;

    @Test
    void findAllByOrganizationId_returnsOnlyMatchingOrgPostings() {
        UUID orgA = UuidFactory.newId();
        UUID orgB = UuidFactory.newId();

        repo.save(posting(orgA, "src", "ext-1"));
        repo.save(posting(orgA, "src", "ext-2"));
        repo.save(posting(orgB, "src", "ext-3"));

        List<JobPostingEntity> orgAPostings = repo.findAllByOrganizationId(orgA);
        assertThat(orgAPostings).hasSize(2);
        assertThat(orgAPostings).allSatisfy(p ->
                assertThat(p.getOrganizationId()).isEqualTo(orgA));
    }

    @Test
    void findAllByOrganizationId_returnsEmptyWhenNoPostings() {
        UUID emptyOrg = UuidFactory.newId();
        assertThat(repo.findAllByOrganizationId(emptyOrg)).isEmpty();
    }

    private static JobPostingEntity posting(UUID orgId, String source, String externalId) {
        var e = new JobPostingEntity();
        e.setId(UuidFactory.newId());
        e.setOrganizationId(orgId);
        e.setSource(source);
        e.setExternalId(externalId);
        e.setRawText("body");
        e.setFetchedAt(Instant.now());
        return e;
    }
}
