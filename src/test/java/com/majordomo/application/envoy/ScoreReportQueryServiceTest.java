package com.majordomo.application.envoy;

import com.majordomo.domain.model.Page;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.envoy.ScoreReport;
import com.majordomo.domain.port.out.envoy.ScoreReportRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScoreReportQueryServiceTest {

    private final UUID orgId = UuidFactory.newId();

    @Test
    void clampsLimitAndDelegatesToRepository() {
        var repo = mock(ScoreReportRepository.class);
        var service = new ScoreReportQueryService(repo);
        when(repo.query(eq(orgId), any(), any(), any(), eq(100)))
                .thenReturn(new Page<>(List.of(), null, false));

        service.query(orgId, 60, Recommendation.APPLY_NOW, null, 500);

        verify(repo).query(orgId, 60, Recommendation.APPLY_NOW, null, 100);
    }

    @Test
    void findByIdDelegates() {
        var repo = mock(ScoreReportRepository.class);
        var expected = new ScoreReport(UuidFactory.newId(), orgId,
                UuidFactory.newId(), UuidFactory.newId(), 1, Optional.empty(),
                List.of(), List.of(),
                10, 10, Recommendation.CONSIDER, "m", Instant.now());
        when(repo.findById(expected.id(), orgId)).thenReturn(Optional.of(expected));

        var service = new ScoreReportQueryService(repo);
        var found = service.findById(expected.id(), orgId).orElseThrow();

        assertThat(found.id()).isEqualTo(expected.id());
    }
}
