package com.majordomo.application.envoy;

import com.majordomo.adapter.out.ingest.JobSource;
import com.majordomo.domain.model.UuidFactory;
import com.majordomo.domain.model.envoy.JobPosting;
import com.majordomo.domain.model.envoy.JobSourceRequest;
import com.majordomo.domain.port.out.envoy.JobPostingRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobIngestionServiceTest {

    private final UUID orgId = UuidFactory.newId();

    @Test
    void routesToFirstSupportingSourceAndPersists() {
        var matching = mock(JobSource.class);
        when(matching.supports(any())).thenReturn(true);
        when(matching.fetch(any())).thenAnswer(inv -> {
            var p = new JobPosting();
            p.setSource("manual");
            p.setRawText("body");
            return p;
        });
        var nonMatching = mock(JobSource.class);
        when(nonMatching.supports(any())).thenReturn(false);

        var repo = mock(JobPostingRepository.class);
        when(repo.save(any())).thenAnswer(inv -> {
            JobPosting p = inv.getArgument(0);
            p.setId(UuidFactory.newId());
            return p;
        });

        var service = new JobIngestionService(List.of(nonMatching, matching), repo);
        JobPosting saved = service.ingest(new JobSourceRequest("manual", "body", Map.of()), orgId);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getOrganizationId()).isEqualTo(orgId);
        verify(matching).fetch(any());
        verify(nonMatching, never()).fetch(any());
    }

    @Test
    void throwsWhenNoSourceSupportsTheRequest() {
        var source = mock(JobSource.class);
        when(source.supports(any())).thenReturn(false);
        var service = new JobIngestionService(List.of(source), mock(JobPostingRepository.class));

        assertThatThrownBy(() -> service.ingest(
                new JobSourceRequest("mystery", "", Map.of()), orgId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reusesExistingPostingWhenSourceAndExternalIdMatch() {
        var existing = new JobPosting();
        existing.setId(UuidFactory.newId());
        existing.setSource("greenhouse");
        existing.setExternalId("abc");
        existing.setOrganizationId(orgId);

        var source = mock(JobSource.class);
        when(source.supports(any())).thenReturn(true);
        when(source.fetch(any())).thenAnswer(inv -> {
            var p = new JobPosting();
            p.setSource("greenhouse");
            p.setExternalId("abc");
            return p;
        });

        var repo = mock(JobPostingRepository.class);
        when(repo.findBySourceAndExternalId("greenhouse", "abc", orgId))
                .thenReturn(Optional.of(existing));

        var service = new JobIngestionService(List.of(source), repo);
        JobPosting result = service.ingest(
                new JobSourceRequest("greenhouse", "abc", Map.of()), orgId);

        assertThat(result.getId()).isEqualTo(existing.getId());
        verify(repo, never()).save(any());
    }
}
