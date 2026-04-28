package com.majordomo.adapter.in.event;

import com.majordomo.domain.model.event.JobPostingScored;
import com.majordomo.domain.model.event.PostingDismissed;
import com.majordomo.domain.model.event.PostingMarkedApplied;
import com.majordomo.domain.model.event.ServiceRecordCreated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Evicts caches in response to domain events to ensure consistency.
 */
@Component
public class CacheEvictionListener {

    private static final Logger LOG = LoggerFactory.getLogger(CacheEvictionListener.class);

    private static final String APPLY_NOW_CACHE = "envoy-apply-now";
    private static final String APPLY_NOW_STAT_CACHE = "envoy-apply-now-stat";

    private final CacheManager cacheManager;

    /**
     * Constructs the listener with the cache manager.
     *
     * @param cacheManager the Spring cache manager
     */
    public CacheEvictionListener(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * Evicts spend cache when a service record is created.
     *
     * @param event the service record creation event
     */
    @EventListener
    public void onServiceRecordCreated(ServiceRecordCreated event) {
        LOG.debug("Evicting spend cache after service record created");
        evict("spend");
    }

    /**
     * Evicts the APPLY_NOW dashboard caches after a posting is scored, so
     * newly-scored APPLY_NOW postings appear promptly on summary surfaces.
     *
     * @param event the scoring event
     */
    @EventListener
    public void onJobPostingScored(JobPostingScored event) {
        LOG.debug("Evicting envoy-apply-now caches after posting scored");
        evict(APPLY_NOW_CACHE);
        evict(APPLY_NOW_STAT_CACHE);
    }

    /**
     * Evicts the APPLY_NOW conversion stat after a user marks a posting applied.
     *
     * @param event the apply event
     */
    @EventListener
    public void onPostingMarkedApplied(PostingMarkedApplied event) {
        LOG.debug("Evicting envoy-apply-now-stat cache after posting marked applied");
        evict(APPLY_NOW_STAT_CACHE);
    }

    /**
     * Evicts the APPLY_NOW conversion stat after a user dismisses a posting.
     *
     * @param event the dismiss event
     */
    @EventListener
    public void onPostingDismissed(PostingDismissed event) {
        LOG.debug("Evicting envoy-apply-now-stat cache after posting dismissed");
        evict(APPLY_NOW_STAT_CACHE);
    }

    private void evict(String cacheName) {
        var cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }
}
