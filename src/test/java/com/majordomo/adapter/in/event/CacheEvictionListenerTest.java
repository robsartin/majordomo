package com.majordomo.adapter.in.event;

import com.majordomo.domain.model.envoy.Recommendation;
import com.majordomo.domain.model.event.JobPostingScored;
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

    /** APPLY_NOW dashboard cache should be cleared when a posting is scored. */
    @Test
    void onJobPostingScoredEvictsApplyNowCache() {
        var cacheManager = mock(CacheManager.class);
        var cache = mock(Cache.class);
        when(cacheManager.getCache("envoy-apply-now")).thenReturn(cache);

        var listener = new CacheEvictionListener(cacheManager);
        listener.onJobPostingScored(new JobPostingScored(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                85, Recommendation.APPLY_NOW, Instant.now()));

        verify(cache).clear();
    }
}
