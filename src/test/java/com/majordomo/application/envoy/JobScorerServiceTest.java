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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    private final UUID orgId = UuidFactory.newId();

    private Rubric rubric;
    private JobPosting posting;

    @BeforeEach
    void setUp() {
        scorer = new JobScorerService(
                rubrics, postings, reports, llm, new ScoreAssembler(), eventPublisher);
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
}
