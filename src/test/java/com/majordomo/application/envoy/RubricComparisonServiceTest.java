package com.majordomo.application.envoy;

import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.LlmScoreResponse;
import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.envoy.Rubric;
import com.majordomo.domain.model.envoy.RubricComparison;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.model.envoy.Thresholds;
import com.majordomo.domain.port.out.envoy.JobPostingRepository;
import com.majordomo.domain.port.out.envoy.LlmScoringPort;
import com.majordomo.domain.port.out.envoy.RubricRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link RubricComparisonService}.
 */
class RubricComparisonServiceTest {

    private static final String RUBRIC_NAME = "default";
    private static final UUID ORG_ID = UUID.randomUUID();

    private final RubricRepository rubrics = mock(RubricRepository.class);
    private final JobPostingRepository postings = mock(JobPostingRepository.class);
    private final LlmScoringPort llm = mock(LlmScoringPort.class);
    private final ScoreAssembler assembler = mock(ScoreAssembler.class);
    private final RubricComparisonService service =
            new RubricComparisonService(rubrics, postings, llm, assembler);

    /** Happy path: two postings, two rubric versions, deltas + aggregates computed correctly. */
    @Test
    void comparesTwoVersionsAcrossPostingSet() {
        Rubric v1 = rubric(1);
        Rubric v2 = rubric(2);
        when(rubrics.findByOrgNameVersion(RUBRIC_NAME, 1, ORG_ID)).thenReturn(Optional.of(v1));
        when(rubrics.findByOrgNameVersion(RUBRIC_NAME, 2, ORG_ID)).thenReturn(Optional.of(v2));

        JobPosting p1 = posting();
        JobPosting p2 = posting();
        when(postings.findRecentByOrganizationId(ORG_ID, 50)).thenReturn(List.of(p1, p2));
        when(llm.modelId()).thenReturn("claude-test");
        when(llm.score(any(), any())).thenReturn(mock(LlmScoreResponse.class));

        // p1: 60 -> 80 (CONSIDER -> APPLY) = flip
        // p2: 90 -> 70 (APPLY_NOW -> APPLY)  = flip
        ScoreReport p1v1 = score(p1, v1, 60, Recommendation.CONSIDER);
        ScoreReport p1v2 = score(p1, v2, 80, Recommendation.APPLY);
        ScoreReport p2v1 = score(p2, v1, 90, Recommendation.APPLY_NOW);
        ScoreReport p2v2 = score(p2, v2, 70, Recommendation.APPLY);
        when(assembler.assemble(eq(p1), eq(v1), any(), any())).thenReturn(p1v1);
        when(assembler.assemble(eq(p1), eq(v2), any(), any())).thenReturn(p1v2);
        when(assembler.assemble(eq(p2), eq(v1), any(), any())).thenReturn(p2v1);
        when(assembler.assemble(eq(p2), eq(v2), any(), any())).thenReturn(p2v2);

        RubricComparison result = service.compare(RUBRIC_NAME, 1, 2, 50, ORG_ID);

        assertThat(result.rubricName()).isEqualTo(RUBRIC_NAME);
        assertThat(result.fromVersion()).isEqualTo(1);
        assertThat(result.toVersion()).isEqualTo(2);
        assertThat(result.postings()).hasSize(2);
        assertThat(result.postings().get(0).scoreDelta()).isEqualTo(20);
        assertThat(result.postings().get(0).recommendationFlipped()).isTrue();
        assertThat(result.postings().get(1).scoreDelta()).isEqualTo(-20);
        assertThat(result.postings().get(1).recommendationFlipped()).isTrue();
        assertThat(result.flips()).isEqualTo(2);
        assertThat(result.meanFromScore()).isEqualTo(75.0);
        assertThat(result.meanToScore()).isEqualTo(75.0);
        assertThat(result.fromDistribution()).containsEntry(Recommendation.CONSIDER, 1L)
                .containsEntry(Recommendation.APPLY_NOW, 1L);
        assertThat(result.toDistribution()).containsEntry(Recommendation.APPLY, 2L);
    }

    /** Live ScoreReport persistence is never invoked — comparison is in-memory only. */
    @Test
    void doesNotPersistReports() {
        Rubric v1 = rubric(1);
        Rubric v2 = rubric(2);
        when(rubrics.findByOrgNameVersion(RUBRIC_NAME, 1, ORG_ID)).thenReturn(Optional.of(v1));
        when(rubrics.findByOrgNameVersion(RUBRIC_NAME, 2, ORG_ID)).thenReturn(Optional.of(v2));
        when(postings.findRecentByOrganizationId(any(), any(Integer.class))).thenReturn(List.of());
        when(llm.modelId()).thenReturn("claude-test");

        service.compare(RUBRIC_NAME, 1, 2, 50, ORG_ID);

        // No save methods on these mocks should ever be called.
        verify(postings, never()).save(any());
        verify(rubrics, never()).save(any());
    }

    /** Missing fromVersion throws IllegalArgumentException. */
    @Test
    void throwsWhenFromVersionMissing() {
        when(rubrics.findByOrgNameVersion(RUBRIC_NAME, 1, ORG_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.compare(RUBRIC_NAME, 1, 2, 50, ORG_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("v1");
    }

    /** Missing toVersion throws IllegalArgumentException. */
    @Test
    void throwsWhenToVersionMissing() {
        when(rubrics.findByOrgNameVersion(RUBRIC_NAME, 1, ORG_ID)).thenReturn(Optional.of(rubric(1)));
        when(rubrics.findByOrgNameVersion(RUBRIC_NAME, 2, ORG_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.compare(RUBRIC_NAME, 1, 2, 50, ORG_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("v2");
    }

    /** Comparing a version against itself is rejected. */
    @Test
    void throwsWhenVersionsAreEqual() {
        assertThatThrownBy(() -> service.compare(RUBRIC_NAME, 3, 3, 50, ORG_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Limit is clamped to [1, 50]. */
    @Test
    void clampsLimit() {
        Rubric v1 = rubric(1);
        Rubric v2 = rubric(2);
        when(rubrics.findByOrgNameVersion(RUBRIC_NAME, 1, ORG_ID)).thenReturn(Optional.of(v1));
        when(rubrics.findByOrgNameVersion(RUBRIC_NAME, 2, ORG_ID)).thenReturn(Optional.of(v2));
        when(postings.findRecentByOrganizationId(eq(ORG_ID), eq(50))).thenReturn(List.of());
        when(llm.modelId()).thenReturn("claude-test");

        service.compare(RUBRIC_NAME, 1, 2, 9999, ORG_ID);

        verify(postings).findRecentByOrganizationId(ORG_ID, 50);
    }

    /** Empty posting set returns zero-state aggregates without calling the LLM. */
    @Test
    void emptyPostingSetReturnsZeroState() {
        Rubric v1 = rubric(1);
        Rubric v2 = rubric(2);
        when(rubrics.findByOrgNameVersion(RUBRIC_NAME, 1, ORG_ID)).thenReturn(Optional.of(v1));
        when(rubrics.findByOrgNameVersion(RUBRIC_NAME, 2, ORG_ID)).thenReturn(Optional.of(v2));
        when(postings.findRecentByOrganizationId(any(), any(Integer.class))).thenReturn(List.of());
        when(llm.modelId()).thenReturn("claude-test");

        RubricComparison result = service.compare(RUBRIC_NAME, 1, 2, 10, ORG_ID);

        assertThat(result.postings()).isEmpty();
        assertThat(result.flips()).isZero();
        assertThat(result.meanFromScore()).isZero();
        assertThat(result.meanToScore()).isZero();
        verify(llm, never()).score(any(), any());
    }

    private static Rubric rubric(int version) {
        return new Rubric(
                UUID.randomUUID(),
                Optional.of(ORG_ID),
                version,
                RUBRIC_NAME,
                List.of(),
                List.of(),
                List.of(),
                new Thresholds(85, 70, 50),
                Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static JobPosting posting() {
        JobPosting p = new JobPosting();
        p.setId(UUID.randomUUID());
        p.setOrganizationId(ORG_ID);
        p.setSource("manual");
        p.setRawText("body");
        return p;
    }

    private static ScoreReport score(JobPosting p, Rubric r, int finalScore, Recommendation rec) {
        return new ScoreReport(
                UUID.randomUUID(), ORG_ID, p.getId(), r.id(), r.version(),
                Optional.empty(), List.of(), List.of(),
                finalScore, finalScore, rec, "claude-test", Instant.now());
    }
}
