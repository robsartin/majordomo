package com.majordomo.application.envoy;

import com.majordomo.domain.model.EntityNotFoundException;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.Category;
import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.LlmScoreResponse;
import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.envoy.Rubric;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.model.envoy.Thresholds;
import com.majordomo.domain.model.envoy.Tier;
import com.majordomo.domain.model.event.JobPostingScored;
import com.majordomo.domain.port.out.EventPublisher;
import com.majordomo.domain.port.out.envoy.JobPostingRepository;
import com.majordomo.domain.port.out.envoy.LlmScoringPort;
import com.majordomo.domain.port.out.envoy.RubricRepository;
import com.majordomo.domain.port.out.envoy.ScoreReportRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobScorerServiceTest {

    @Mock RubricRepository rubrics;
    @Mock JobPostingRepository postings;
    @Mock ScoreReportRepository reports;
    @Mock LlmScoringPort llm;
    @Mock EventPublisher eventPublisher;

    private JobScorerService scorer;
    private MeterRegistry meterRegistry;
    private final UUID orgId = UuidFactory.newId();

    private Rubric rubric;
    private JobPosting posting;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        scorer = new JobScorerService(
                rubrics, postings, reports, llm, new ScoreAssembler(),
                eventPublisher, new LlmCallObserver(new EnvoyMetrics(meterRegistry)));
        rubric = new Rubric(UuidFactory.newId(), Optional.empty(), 1, "default",
                List.of(),
                List.of(new Category("compensation", "pay", 20,
                        List.of(new Tier("Good", 15, "$200-250k")))),
                List.of(), new Thresholds(20, 15, 5), Instant.now());
        posting = new JobPosting();
        posting.setId(UuidFactory.newId());
        posting.setOrganizationId(orgId);
        posting.setRawText("We pay $220k");
    }

    @Test
    void scorePersistsReportAndReturnsIt() {
        when(postings.findById(posting.getId(), orgId)).thenReturn(Optional.of(posting));
        when(rubrics.findActiveByName("default", orgId)).thenReturn(Optional.of(rubric));
        when(llm.score(any(), any())).thenReturn(LlmScoreResponse.of(null,
                List.of(new LlmScoreResponse.CategoryVerdict("compensation", "Good", "listed")),
                List.of()));
        when(llm.modelId()).thenReturn("claude-sonnet-4-6");
        when(reports.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ScoreReport r = scorer.score(posting.getId(), "default", orgId);

        assertThat(r.finalScore()).isEqualTo(15);
        assertThat(r.recommendation()).isEqualTo(Recommendation.APPLY);
        assertThat(r.llmModel()).isEqualTo("claude-sonnet-4-6");
        verify(eventPublisher).publish(any(JobPostingScored.class));
    }

    @Test
    void throwsWhenPostingMissing() {
        UUID bogus = UuidFactory.newId();
        when(postings.findById(bogus, orgId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> scorer.score(bogus, "default", orgId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void throwsWhenRubricMissing() {
        when(postings.findById(posting.getId(), orgId)).thenReturn(Optional.of(posting));
        when(rubrics.findActiveByName("default", orgId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> scorer.score(posting.getId(), "default", orgId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void scoreAllPersistsOneReportPerRubricAndPublishesOneEventEach() {
        Rubric rubricBackend = new Rubric(UuidFactory.newId(), Optional.empty(), 1, "backend",
                List.of(),
                List.of(new Category("compensation", "pay", 20,
                        List.of(new Tier("Good", 15, "$200-250k")))),
                List.of(), new Thresholds(20, 15, 5), Instant.now());
        Rubric rubricStaffPlus = new Rubric(UuidFactory.newId(), Optional.empty(), 2, "staff-plus",
                List.of(),
                List.of(new Category("compensation", "pay", 20,
                        List.of(new Tier("Good", 15, "$200-250k")))),
                List.of(), new Thresholds(20, 15, 5), Instant.now());
        when(postings.findById(posting.getId(), orgId)).thenReturn(Optional.of(posting));
        when(rubrics.findActiveByName("backend", orgId)).thenReturn(Optional.of(rubricBackend));
        when(rubrics.findActiveByName("staff-plus", orgId)).thenReturn(Optional.of(rubricStaffPlus));
        when(llm.score(any(), any())).thenReturn(LlmScoreResponse.of(null,
                List.of(new LlmScoreResponse.CategoryVerdict("compensation", "Good", "listed")),
                List.of()));
        when(llm.modelId()).thenReturn("claude-sonnet-4-6");
        when(reports.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<ScoreReport> saved = scorer.scoreAll(
                posting.getId(), List.of("backend", "staff-plus"), orgId);

        assertThat(saved).hasSize(2);
        assertThat(saved).allSatisfy(r -> assertThat(r.postingId()).isEqualTo(posting.getId()));
        assertThat(saved).extracting(ScoreReport::rubricId)
                .containsExactly(rubricBackend.id(), rubricStaffPlus.id());
        assertThat(saved).extracting(ScoreReport::rubricVersion)
                .containsExactly(1, 2);

        ArgumentCaptor<ScoreReport> savedReports = ArgumentCaptor.forClass(ScoreReport.class);
        verify(reports, times(2)).save(savedReports.capture());
        verify(eventPublisher, times(2)).publish(any(JobPostingScored.class));
    }

    @Test
    void scoreAllFailsFastWhenAnyRubricMissing() {
        Rubric rubricBackend = new Rubric(UuidFactory.newId(), Optional.empty(), 1, "backend",
                List.of(),
                List.of(new Category("compensation", "pay", 20,
                        List.of(new Tier("Good", 15, "$200-250k")))),
                List.of(), new Thresholds(20, 15, 5), Instant.now());
        when(postings.findById(posting.getId(), orgId)).thenReturn(Optional.of(posting));
        when(rubrics.findActiveByName("backend", orgId)).thenReturn(Optional.of(rubricBackend));
        when(rubrics.findActiveByName("missing", orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scorer.scoreAll(
                posting.getId(), List.of("backend", "missing"), orgId))
                .isInstanceOf(EntityNotFoundException.class);

        // No partial state: nothing persisted, no events published.
        verify(reports, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void scoreAllRejectsEmptyRubricList() {
        assertThatThrownBy(() -> scorer.scoreAll(posting.getId(), List.of(), orgId))
                .isInstanceOf(IllegalArgumentException.class);
        verify(reports, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void recordsTokenAndLatencyMetricsWhenLlmReturnsUsage() {
        when(postings.findById(posting.getId(), orgId)).thenReturn(Optional.of(posting));
        when(rubrics.findActiveByName("default", orgId)).thenReturn(Optional.of(rubric));
        when(llm.score(any(), any())).thenReturn(new LlmScoreResponse(
                Optional.empty(),
                List.of(new LlmScoreResponse.CategoryVerdict("compensation", "Good", "listed")),
                List.of(),
                Optional.of(new LlmScoreResponse.Usage(123L, 45L, 200L))));
        when(llm.modelId()).thenReturn("claude-sonnet-4-6");
        when(reports.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scorer.score(posting.getId(), "default", orgId);

        double inputTokens = meterRegistry.get("envoy_llm_input_tokens_total")
                .tag("org", orgId.toString())
                .tag("model", "claude-sonnet-4-6")
                .tag("rubric", "default")
                .counter().count();
        double outputTokens = meterRegistry.get("envoy_llm_output_tokens_total")
                .tag("org", orgId.toString())
                .tag("model", "claude-sonnet-4-6")
                .tag("rubric", "default")
                .counter().count();
        long timerCount = meterRegistry.get("envoy_llm_call_duration")
                .tag("model", "claude-sonnet-4-6")
                .tag("outcome", "success")
                .timer().count();

        assertThat(inputTokens).isEqualTo(123.0);
        assertThat(outputTokens).isEqualTo(45.0);
        assertThat(timerCount).isEqualTo(1L);
    }

    @Test
    void doesNotIncrementTokenCountersWhenLlmReturnsNoUsage() {
        when(postings.findById(posting.getId(), orgId)).thenReturn(Optional.of(posting));
        when(rubrics.findActiveByName("default", orgId)).thenReturn(Optional.of(rubric));
        when(llm.score(any(), any())).thenReturn(LlmScoreResponse.of(null,
                List.of(new LlmScoreResponse.CategoryVerdict("compensation", "Good", "listed")),
                List.of()));
        when(llm.modelId()).thenReturn("claude-sonnet-4-6");
        when(reports.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ScoreReport r = scorer.score(posting.getId(), "default", orgId);

        assertThat(r).isNotNull();
        // Counters were never registered, so finding them should produce no meter.
        assertThat(meterRegistry.find("envoy_llm_input_tokens_total").counter()).isNull();
        assertThat(meterRegistry.find("envoy_llm_output_tokens_total").counter()).isNull();
        // Timer is always recorded so latency is observable even without token data.
        long timerCount = meterRegistry.get("envoy_llm_call_duration")
                .tag("model", "claude-sonnet-4-6")
                .tag("outcome", "success")
                .timer().count();
        assertThat(timerCount).isEqualTo(1L);
    }

    @Test
    void recordsErrorOutcomeOnTimerWhenLlmThrows() {
        when(postings.findById(posting.getId(), orgId)).thenReturn(Optional.of(posting));
        when(rubrics.findActiveByName("default", orgId)).thenReturn(Optional.of(rubric));
        when(llm.modelId()).thenReturn("claude-sonnet-4-6");
        when(llm.score(any(), any())).thenThrow(new LlmScoringException("boom"));

        assertThatThrownBy(() -> scorer.score(posting.getId(), "default", orgId))
                .isInstanceOf(LlmScoringException.class);

        long errorCount = meterRegistry.get("envoy_llm_call_duration")
                .tag("model", "claude-sonnet-4-6")
                .tag("outcome", "error")
                .timer().count();
        assertThat(errorCount).isEqualTo(1L);
        // No success timer was recorded.
        assertThat(meterRegistry.find("envoy_llm_call_duration")
                .tag("outcome", "success").timer()).isNull();
        // No token counters either, since the call never produced a usage payload.
        assertThat(meterRegistry.find("envoy_llm_input_tokens_total").counter()).isNull();
        assertThat(meterRegistry.find("envoy_llm_output_tokens_total").counter()).isNull();
    }

    @Test
    void persistedReportCarriesUsageFromLlmResponse() {
        var usage = new LlmScoreResponse.Usage(987L, 654L, 321L);
        when(postings.findById(posting.getId(), orgId)).thenReturn(Optional.of(posting));
        when(rubrics.findActiveByName("default", orgId)).thenReturn(Optional.of(rubric));
        when(llm.score(any(), any())).thenReturn(new LlmScoreResponse(
                Optional.empty(),
                List.of(new LlmScoreResponse.CategoryVerdict("compensation", "Good", "listed")),
                List.of(),
                Optional.of(usage)));
        when(llm.modelId()).thenReturn("claude-sonnet-4-6");
        when(reports.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scorer.score(posting.getId(), "default", orgId);

        ArgumentCaptor<ScoreReport> savedCaptor = ArgumentCaptor.forClass(ScoreReport.class);
        verify(reports).save(savedCaptor.capture());
        ScoreReport persisted = savedCaptor.getValue();
        assertThat(persisted.usage()).contains(usage);
    }

    @Test
    void persistedReportHasEmptyUsageWhenLlmReturnsNone() {
        when(postings.findById(posting.getId(), orgId)).thenReturn(Optional.of(posting));
        when(rubrics.findActiveByName("default", orgId)).thenReturn(Optional.of(rubric));
        when(llm.score(any(), any())).thenReturn(LlmScoreResponse.of(null,
                List.of(new LlmScoreResponse.CategoryVerdict("compensation", "Good", "listed")),
                List.of()));
        when(llm.modelId()).thenReturn("claude-sonnet-4-6");
        when(reports.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scorer.score(posting.getId(), "default", orgId);

        ArgumentCaptor<ScoreReport> savedCaptor = ArgumentCaptor.forClass(ScoreReport.class);
        verify(reports).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().usage()).isEmpty();
    }

    @Test
    void scoreAllSingleRubricStillWorks() {
        when(postings.findById(posting.getId(), orgId)).thenReturn(Optional.of(posting));
        when(rubrics.findActiveByName("default", orgId)).thenReturn(Optional.of(rubric));
        when(llm.score(any(), any())).thenReturn(LlmScoreResponse.of(null,
                List.of(new LlmScoreResponse.CategoryVerdict("compensation", "Good", "listed")),
                List.of()));
        when(llm.modelId()).thenReturn("claude-sonnet-4-6");
        when(reports.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<ScoreReport> saved = scorer.scoreAll(
                posting.getId(), List.of("default"), orgId);

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).postingId()).isEqualTo(posting.getId());
        verify(eventPublisher, times(1)).publish(any(JobPostingScored.class));
    }
}
