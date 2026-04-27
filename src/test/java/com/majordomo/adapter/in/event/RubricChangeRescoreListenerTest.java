package com.majordomo.adapter.in.event;

import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.event.RubricVersionCreated;
import com.majordomo.domain.port.in.envoy.ScoreJobPostingUseCase;
import com.majordomo.domain.port.out.envoy.JobPostingRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link RubricChangeRescoreListener} — the automatic-rescore
 * half of issue #147.
 */
class RubricChangeRescoreListenerTest {

    @Test
    void onRubricVersionCreated_scoresEachPostingInOrg() {
        UUID orgId = UuidFactory.newId();
        var postings = mock(JobPostingRepository.class);
        var scoreUseCase = mock(ScoreJobPostingUseCase.class);

        var p1 = posting(orgId);
        var p2 = posting(orgId);
        when(postings.findAllByOrganizationId(orgId)).thenReturn(List.of(p1, p2));

        var listener = new RubricChangeRescoreListener(postings, scoreUseCase);
        listener.onRubricVersionCreated(new RubricVersionCreated(
                orgId, "default", 4, Instant.now()));

        verify(scoreUseCase).score(eq(p1.getId()), eq("default"), eq(orgId));
        verify(scoreUseCase).score(eq(p2.getId()), eq("default"), eq(orgId));
    }

    @Test
    void onRubricVersionCreated_zeroPostings_noopButExitsCleanly() {
        UUID orgId = UuidFactory.newId();
        var postings = mock(JobPostingRepository.class);
        var scoreUseCase = mock(ScoreJobPostingUseCase.class);
        when(postings.findAllByOrganizationId(orgId)).thenReturn(List.of());

        var listener = new RubricChangeRescoreListener(postings, scoreUseCase);
        listener.onRubricVersionCreated(new RubricVersionCreated(
                orgId, "default", 1, Instant.now()));

        verify(scoreUseCase, never()).score(eq(null), eq("default"), eq(orgId));
    }

    @Test
    void onRubricVersionCreated_continuesPastFailureOnSinglePosting() {
        UUID orgId = UuidFactory.newId();
        var postings = mock(JobPostingRepository.class);
        var scoreUseCase = mock(ScoreJobPostingUseCase.class);
        var p1 = posting(orgId);
        var p2 = posting(orgId);
        when(postings.findAllByOrganizationId(orgId)).thenReturn(List.of(p1, p2));
        when(scoreUseCase.score(eq(p1.getId()), eq("default"), eq(orgId)))
                .thenThrow(new RuntimeException("LLM circuit open"));

        var listener = new RubricChangeRescoreListener(postings, scoreUseCase);
        listener.onRubricVersionCreated(new RubricVersionCreated(
                orgId, "default", 2, Instant.now()));

        // p2 still gets scored even after p1 fails
        verify(scoreUseCase).score(eq(p2.getId()), eq("default"), eq(orgId));
    }

    private static JobPosting posting(UUID orgId) {
        var p = new JobPosting();
        p.setId(UuidFactory.newId());
        p.setOrganizationId(orgId);
        p.setSource("manual");
        p.setRawText("body");
        return p;
    }
}
