package com.majordomo.adapter.out.persistence.envoy;

import com.majordomo.IntegrationTest;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.ApplicationMaterial;
import com.majordomo.domain.model.envoy.ClaimCitation;
import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.LlmScoreResponse;
import com.majordomo.domain.model.envoy.MaterialKind;
import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.model.envoy.Tone;
import com.majordomo.domain.model.identity.Organization;
import com.majordomo.domain.port.out.envoy.ApplicationMaterialRepository;
import com.majordomo.domain.port.out.envoy.JobPostingRepository;
import com.majordomo.domain.port.out.envoy.ScoreReportRepository;
import com.majordomo.domain.port.out.identity.OrganizationRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Persistence for generated drafts, against real PostgreSQL (#348).
 *
 * <p>The draft and its citations live in a JSONB body, so the round trip is
 * the thing worth testing: a claim that does not survive storage is a claim
 * nobody can check later, which would quietly undo the point of recording it.
 */
@IntegrationTest
class ApplicationMaterialRepositoryIntegrationTest {

    /** Seeded by V15 as the system default, organization_id IS NULL. */
    private static final UUID SEEDED_DEFAULT_RUBRIC =
            UUID.fromString("01910000-0000-7000-8000-000000000001");

    @Autowired
    private ApplicationMaterialRepository materials;

    @Autowired
    private JobPostingRepository postings;

    @Autowired
    private ScoreReportRepository reports;

    @Autowired
    private OrganizationRepository organizations;

    @Test
    void savedMaterial_comesBackWithItsClaimsIntact() {
        UUID org = newOrg();
        UUID posting = newPosting(org);
        ApplicationMaterial saved = materials.save(material(org, posting,
                MaterialKind.COVER_LETTER,
                List.of(new ClaimCitation("Ten years of Java", "Java, Spring Boot, Postgres"),
                        new ClaimCitation("Led a team", "Led a team of four engineers"))));

        ApplicationMaterial found = materials.findById(saved.id(), org).orElseThrow();

        assertThat(found.content()).isEqualTo("Dear hiring manager");
        assertThat(found.kind()).isEqualTo(MaterialKind.COVER_LETTER);
        assertThat(found.tone()).isEqualTo(Tone.DIRECT);
        assertThat(found.claims())
                .extracting(ClaimCitation::claim, ClaimCitation::source)
                .containsExactly(
                        tuple("Ten years of Java", "Java, Spring Boot, Postgres"),
                        tuple("Led a team", "Led a team of four engineers"));
    }

    @Test
    void usageAndScoreReportLink_surviveTheRoundTrip() {
        UUID org = newOrg();
        UUID posting = newPosting(org);
        UUID reportId = newScoreReport(org, posting);

        ApplicationMaterial saved = materials.save(new ApplicationMaterial(
                UuidFactory.newId(), org, posting, Optional.of(reportId),
                MaterialKind.INTRO_MESSAGE, Tone.WARM, "Hello",
                List.of(), "claude-opus-5", Instant.now(),
                Optional.of(new LlmScoreResponse.Usage(120L, 340L, 900L))));

        ApplicationMaterial found = materials.findById(saved.id(), org).orElseThrow();

        assertThat(found.scoreReportId()).contains(reportId);
        assertThat(found.usage()).isPresent();
        assertThat(found.usage().orElseThrow().outputTokens()).isEqualTo(340L);
    }

    /** Drafts accumulate rather than replace, so a posting's history is a list. */
    @Test
    void findByPosting_returnsEveryDraftNewestFirst() {
        UUID org = newOrg();
        UUID posting = newPosting(org);
        materials.save(material(org, posting, MaterialKind.COVER_LETTER, List.of()));
        materials.save(material(org, posting, MaterialKind.INTRO_MESSAGE, List.of()));

        assertThat(materials.findByPosting(posting, org))
                .extracting(ApplicationMaterial::kind)
                .containsExactly(MaterialKind.INTRO_MESSAGE, MaterialKind.COVER_LETTER);
    }

    /** Another org's draft is not readable, the way every other Envoy row works. */
    @Test
    void findById_isScopedToTheOrganization() {
        UUID org = newOrg();
        UUID posting = newPosting(org);
        ApplicationMaterial saved =
                materials.save(material(org, posting, MaterialKind.COVER_LETTER, List.of()));

        assertThat(materials.findById(saved.id(), newOrg())).isEmpty();
    }

    private UUID newOrg() {
        UUID id = UuidFactory.newId();
        organizations.save(new Organization(id, "org-" + id));
        return id;
    }

    private UUID newPosting(UUID orgId) {
        JobPosting posting = new JobPosting();
        posting.setId(UuidFactory.newId());
        posting.setOrganizationId(orgId);
        posting.setSource("manual");
        posting.setCompany("Acme");
        posting.setTitle("Staff Engineer");
        posting.setRawText("Java, Spring, fully remote.");
        posting.setFetchedAt(Instant.now());
        return postings.save(posting).getId();
    }

    /**
     * A real report row, because score_report_id carries a foreign key. An
     * invented id passes in memory and fails at the database, which is the
     * constraint doing its job.
     */
    private UUID newScoreReport(UUID orgId, UUID postingId) {
        return reports.save(new ScoreReport(
                UuidFactory.newId(), orgId, postingId, SEEDED_DEFAULT_RUBRIC, 1,
                Optional.empty(), List.of(), List.of(), 60, 60,
                Recommendation.APPLY, "claude-sonnet-4-6", Instant.now(),
                Optional.empty(), Optional.empty())).id();
    }

    private static ApplicationMaterial material(
            UUID org, UUID posting, MaterialKind kind, List<ClaimCitation> claims) {
        return new ApplicationMaterial(
                UuidFactory.newId(), org, posting, Optional.empty(), kind, Tone.DIRECT,
                "Dear hiring manager", claims, "claude-opus-5", Instant.now(),
                Optional.empty());
    }
}
