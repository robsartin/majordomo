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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link CacheEvictionListener}.
 */
class CacheEvictionListenerTest {

    /** Spend cache should be cleared when a service record is created. */
    @Test
    void onServiceRecordCreatedEvictsSpendCache() {
        var cacheManager = mock(CacheManager.class);
        var cache = mock(Cache.class);
        when(cacheManager.getCache("spend")).thenReturn(cache);

        var listener = new CacheEvictionListener(cacheManager);
        listener.onServiceRecordCreated(new ServiceRecordCreated(
                UUID.randomUUID(), UUID.randomUUID(), null, Instant.now()));

        verify(cache).clear();
    }

    /** APPLY_NOW dashboard caches (postings + stat) should both be cleared when a posting is scored. */
    @Test
    void onJobPostingScoredEvictsApplyNowCaches() {
        var cacheManager = mock(CacheManager.class);
        var postingsCache = mock(Cache.class);
        var statCache = mock(Cache.class);
        when(cacheManager.getCache("envoy-apply-now")).thenReturn(postingsCache);
        when(cacheManager.getCache("envoy-apply-now-stat")).thenReturn(statCache);

        var listener = new CacheEvictionListener(cacheManager);
        listener.onJobPostingScored(new JobPostingScored(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                85, Recommendation.APPLY_NOW, Instant.now()));

        verify(postingsCache).clear();
        verify(statCache).clear();
    }

    /** PostingMarkedApplied should evict the conversion stat cache. */
    @Test
    void onPostingMarkedAppliedEvictsStatCache() {
        var cacheManager = mock(CacheManager.class);
        var statCache = mock(Cache.class);
        when(cacheManager.getCache("envoy-apply-now-stat")).thenReturn(statCache);

        var listener = new CacheEvictionListener(cacheManager);
        listener.onPostingMarkedApplied(new PostingMarkedApplied(
                UUID.randomUUID(), UUID.randomUUID(), Instant.now()));

        verify(statCache).clear();
    }

    /** PostingDismissed should evict the conversion stat cache. */
    @Test
    void onPostingDismissedEvictsStatCache() {
        var cacheManager = mock(CacheManager.class);
        var statCache = mock(Cache.class);
        when(cacheManager.getCache("envoy-apply-now-stat")).thenReturn(statCache);

        var listener = new CacheEvictionListener(cacheManager);
        listener.onPostingDismissed(new PostingDismissed(
                UUID.randomUUID(), UUID.randomUUID(), Instant.now()));

        verify(statCache).clear();
    }
}
