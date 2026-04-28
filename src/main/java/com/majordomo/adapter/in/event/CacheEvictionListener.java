package com.majordomo.adapter.in.event;

import com.majordomo.domain.model.event.JobPostingScored;
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
     * Evicts the APPLY_NOW dashboard cache after a posting is scored, so
     * newly-scored APPLY_NOW postings appear promptly on the dashboard panel.
     *
     * @param event the scoring event
     */
    @EventListener
    public void onJobPostingScored(JobPostingScored event) {
        LOG.debug("Evicting envoy-apply-now cache after posting scored");
        evict("envoy-apply-now");
    }

    private void evict(String cacheName) {
        var cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }
}
