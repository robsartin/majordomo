package com.majordomo.adapter.in.event;

import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.event.JobPostingScored;
import com.majordomo.domain.model.event.PostingDismissed;
import com.majordomo.domain.model.event.PostingMarkedApplied;
import com.majordomo.domain.model.event.ServiceRecordCreated;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link CacheEvictionListener}. The listener routes every supported
 * domain event through a single dispatcher that consults a class &rarr; cache
 * registry; these tests exercise that single entry point.
 */
class CacheEvictionListenerTest {

    /** Spend cache should be cleared when a service record is created. */
    @Test
    void serviceRecordCreatedEvictsSpendCache() {
        var cacheManager = mock(CacheManager.class);
        var cache = mock(Cache.class);
        when(cacheManager.getCache("spend")).thenReturn(cache);

        var listener = new CacheEvictionListener(cacheManager);
        listener.onDomainEvent(new ServiceRecordCreated(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, Instant.now()));

        verify(cache).clear();
    }

    /** APPLY_NOW dashboard caches (postings + stat) should both be cleared when a posting is scored. */
    @Test
    void jobPostingScoredEvictsApplyNowCaches() {
        var cacheManager = mock(CacheManager.class);
        var postingsCache = mock(Cache.class);
        var statCache = mock(Cache.class);
        when(cacheManager.getCache("envoy-apply-now")).thenReturn(postingsCache);
        when(cacheManager.getCache("envoy-apply-now-stat")).thenReturn(statCache);

        var listener = new CacheEvictionListener(cacheManager);
        listener.onDomainEvent(new JobPostingScored(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                85, Recommendation.APPLY_NOW, Instant.now()));

        verify(postingsCache).clear();
        verify(statCache).clear();
    }

    /** PostingMarkedApplied should evict the conversion stat cache. */
    @Test
    void postingMarkedAppliedEvictsStatCache() {
        var cacheManager = mock(CacheManager.class);
        var statCache = mock(Cache.class);
        when(cacheManager.getCache("envoy-apply-now-stat")).thenReturn(statCache);

        var listener = new CacheEvictionListener(cacheManager);
        listener.onDomainEvent(new PostingMarkedApplied(
                UUID.randomUUID(), UUID.randomUUID(), Instant.now()));

        verify(statCache).clear();
    }

    /** PostingDismissed should evict the conversion stat cache. */
    @Test
    void postingDismissedEvictsStatCache() {
        var cacheManager = mock(CacheManager.class);
        var statCache = mock(Cache.class);
        when(cacheManager.getCache("envoy-apply-now-stat")).thenReturn(statCache);

        var listener = new CacheEvictionListener(cacheManager);
        listener.onDomainEvent(new PostingDismissed(
                UUID.randomUUID(), UUID.randomUUID(), Instant.now()));

        verify(statCache).clear();
    }

    /**
     * Unmapped event types must be a no-op so the dispatcher can safely listen
     * to a broad set of classes without misrouting.
     */
    @Test
    void unmappedEventEvictsNothing() {
        var cacheManager = mock(CacheManager.class);

        var listener = new CacheEvictionListener(cacheManager);
        listener.onDomainEvent(new Object());

        verify(cacheManager, never()).getCache(org.mockito.ArgumentMatchers.anyString());
    }
}
