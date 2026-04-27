package com.majordomo.application.envoy;

import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.Category;
import com.majordomo.domain.model.envoy.Confidence;
import com.majordomo.domain.model.envoy.Disqualifier;
import com.majordomo.domain.model.envoy.Flag;
import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.LlmScoreResponse;
import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.envoy.Rubric;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.model.envoy.Thresholds;
import com.majordomo.domain.model.envoy.Tier;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScoreAssemblerTest {

    private final UUID orgId = UuidFactory.newId();

    private final Rubric rubric = new Rubric(
            UuidFactory.newId(), Optional.empty(), 1, "default",
            List.of(new Disqualifier("ON_SITE", "on-site required")),
            List.of(
                    new Category("compensation", "pay", 20, List.of(
                            new Tier("Excellent", 20, ">$250k"),
                            new Tier("Good", 15, "$200-250k"),
                            new Tier("Fair", 8, "$150-200k"))),
                    new Category("remote", "remote friendly", 10, List.of(
                            new Tier("Full remote", 10, "remote allowed"),
                            new Tier("Hybrid", 5, "some days required")))
            ),
            List.of(new Flag("AT_WILL", "aggressive at-will", 3)),
            new Thresholds(25, 20, 10),
            Instant.now());

    private final JobPosting posting = buildPosting();

    private JobPosting buildPosting() {
        var p = new JobPosting();
        p.setId(UuidFactory.newId());
        p.setOrganizationId(orgId);
        p.setSource("manual");
        return p;
    }

    private ScoreAssembler assembler() {
        return new ScoreAssembler();
    }

    @Test
    void sumsTierPointsAndSubtractsFlagPenalties() {
        var resp = LlmScoreResponse.of(null,
                List.of(
                        new LlmScoreResponse.CategoryVerdict("compensation", "Good", "salary listed"),
                        new LlmScoreResponse.CategoryVerdict("remote", "Full remote", "remote allowed")),
                List.of(new LlmScoreResponse.FlagFinding("AT_WILL", "at-will clause")));

        ScoreReport report = assembler().assemble(posting, rubric, resp, "claude-sonnet-4-6");

        assertThat(report.rawScore()).isEqualTo(25);
        assertThat(report.finalScore()).isEqualTo(22);
        assertThat(report.recommendation()).isEqualTo(Recommendation.APPLY);
        assertThat(report.disqualifiedBy()).isEmpty();
        assertThat(report.llmModel()).isEqualTo("claude-sonnet-4-6");
        assertThat(report.organizationId()).isEqualTo(orgId);
    }

    @Test
    void disqualifierForcesSkipAndZeroFinal() {
        var resp = LlmScoreResponse.of("ON_SITE", List.of(), List.of());
        var report = assembler().assemble(posting, rubric, resp, "claude-sonnet-4-6");

        assertThat(report.recommendation()).isEqualTo(Recommendation.SKIP);
        assertThat(report.finalScore()).isEqualTo(0);
        assertThat(report.disqualifiedBy()).isPresent();
        assertThat(report.disqualifiedBy().get().key()).isEqualTo("ON_SITE");
    }

    @Test
    void finalScoreFlooredAtZero() {
        var cheapRubric = new Rubric(UuidFactory.newId(), Optional.empty(), 1, "tiny",
                List.of(),
                List.of(new Category("c", "x", 5,
                        List.of(new Tier("Low", 2, "low")))),
                List.of(new Flag("BIG", "big", 100)),
                new Thresholds(100, 50, 10), Instant.now());
        var resp = LlmScoreResponse.of(null,
                List.of(new LlmScoreResponse.CategoryVerdict("c", "Low", "r")),
                List.of(new LlmScoreResponse.FlagFinding("BIG", "r")));
        var report = assembler().assemble(posting, cheapRubric, resp, "m");
        assertThat(report.finalScore()).isEqualTo(0);
    }

    @Test
    void unknownCategoryKeyThrows() {
        var resp = LlmScoreResponse.of(null,
                List.of(new LlmScoreResponse.CategoryVerdict("bogus", "Good", "r")),
                List.of());
        assertThatThrownBy(() -> assembler().assemble(posting, rubric, resp, "m"))
                .isInstanceOf(LlmScoringException.class)
                .hasMessageContaining("bogus");
    }

    @Test
    void unknownTierLabelThrows() {
        var resp = LlmScoreResponse.of(null,
                List.of(new LlmScoreResponse.CategoryVerdict("compensation", "Stupendous", "r"),
                        new LlmScoreResponse.CategoryVerdict("remote", "Hybrid", "r")),
                List.of());
        assertThatThrownBy(() -> assembler().assemble(posting, rubric, resp, "m"))
                .isInstanceOf(LlmScoringException.class)
                .hasMessageContaining("Stupendous");
    }

    @Test
    void unknownFlagKeyThrows() {
        var resp = LlmScoreResponse.of(null,
                List.of(new LlmScoreResponse.CategoryVerdict("compensation", "Good", "r"),
                        new LlmScoreResponse.CategoryVerdict("remote", "Hybrid", "r")),
                List.of(new LlmScoreResponse.FlagFinding("BOGUS", "r")));
        assertThatThrownBy(() -> assembler().assemble(posting, rubric, resp, "m"))
                .isInstanceOf(LlmScoringException.class)
                .hasMessageContaining("BOGUS");
    }

    @Test
    void unknownDisqualifierKeyThrows() {
        var resp = LlmScoreResponse.of("BOGUS", List.of(), List.of());
        assertThatThrownBy(() -> assembler().assemble(posting, rubric, resp, "m"))
                .isInstanceOf(LlmScoringException.class);
    }

    @Test
    void missingCategoryCoverageThrows() {
        var resp = LlmScoreResponse.of(null,
                List.of(new LlmScoreResponse.CategoryVerdict("compensation", "Good", "r")),
                List.of());
        assertThatThrownBy(() -> assembler().assemble(posting, rubric, resp, "m"))
                .isInstanceOf(LlmScoringException.class)
                .hasMessageContaining("remote");
    }

    @Test
    void confidenceFlowsFromVerdictToCategoryScore() {
        var resp = LlmScoreResponse.of(null,
                List.of(
                        new LlmScoreResponse.CategoryVerdict(
                                "compensation", "Good", "salary listed", Optional.of(Confidence.HIGH)),
                        new LlmScoreResponse.CategoryVerdict(
                                "remote", "Hybrid", "ambiguous wording", Optional.of(Confidence.LOW))),
                List.of());

        ScoreReport report = assembler().assemble(posting, rubric, resp, "m");

        assertThat(report.categoryScores())
                .extracting("categoryKey", "confidence")
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple("compensation", Optional.of(Confidence.HIGH)),
                        org.assertj.core.api.Assertions.tuple("remote", Optional.of(Confidence.LOW)));
    }

    @Test
    void missingConfidenceProducesEmptyOptional() {
        var resp = LlmScoreResponse.of(null,
                List.of(
                        new LlmScoreResponse.CategoryVerdict("compensation", "Good", "salary listed"),
                        new LlmScoreResponse.CategoryVerdict("remote", "Hybrid", "some days required")),
                List.of());

        ScoreReport report = assembler().assemble(posting, rubric, resp, "m");

        assertThat(report.categoryScores()).allSatisfy(cs ->
                assertThat(cs.confidence()).isEmpty());
    }

    @Test
    void usageFromLlmResponseFlowsIntoScoreReport() {
        var usage = new LlmScoreResponse.Usage(1234L, 567L, 890L);
        var resp = new LlmScoreResponse(
                Optional.empty(),
                List.of(
                        new LlmScoreResponse.CategoryVerdict("compensation", "Good", "salary listed"),
                        new LlmScoreResponse.CategoryVerdict("remote", "Hybrid", "some days required")),
                List.of(),
                Optional.of(usage));

        ScoreReport report = assembler().assemble(posting, rubric, resp, "claude-sonnet-4-6");

        assertThat(report.usage()).contains(usage);
    }

    @Test
    void usageIsEmptyWhenLlmResponseHasNone() {
        var resp = LlmScoreResponse.of(null,
                List.of(
                        new LlmScoreResponse.CategoryVerdict("compensation", "Good", "salary listed"),
                        new LlmScoreResponse.CategoryVerdict("remote", "Hybrid", "ambiguous wording")),
                List.of());

        ScoreReport report = assembler().assemble(posting, rubric, resp, "claude-sonnet-4-6");

        assertThat(report.usage()).isEmpty();
    }

    @Test
    void usageStillPreservedOnDisqualifierPath() {
        var usage = new LlmScoreResponse.Usage(10L, 20L, 30L);
        var resp = new LlmScoreResponse(
                Optional.of("ON_SITE"),
                List.of(),
                List.of(),
                Optional.of(usage));

        ScoreReport report = assembler().assemble(posting, rubric, resp, "claude-sonnet-4-6");

        assertThat(report.recommendation()).isEqualTo(Recommendation.SKIP);
        assertThat(report.usage()).contains(usage);
    }

    @Test
    void allConfidenceLevelsAccepted() {
        for (Confidence c : Confidence.values()) {
            var resp = LlmScoreResponse.of(null,
                    List.of(
                            new LlmScoreResponse.CategoryVerdict(
                                    "compensation", "Good", "r", Optional.of(c)),
                            new LlmScoreResponse.CategoryVerdict(
                                    "remote", "Hybrid", "r", Optional.of(c))),
                    List.of());

            ScoreReport report = assembler().assemble(posting, rubric, resp, "m");

            assertThat(report.categoryScores())
                    .allSatisfy(cs -> assertThat(cs.confidence()).contains(c));
        }
    }
}
