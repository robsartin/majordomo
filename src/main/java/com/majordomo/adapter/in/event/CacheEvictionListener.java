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

import java.util.Map;
import java.util.Set;

/**
 * Evicts caches in response to domain events. A static class &rarr; cache-name
 * registry maps each handled event type to the set of caches its arrival
 * invalidates; a single dispatcher consumes the registered events and clears
 * the corresponding caches.
 *
 * <p>Adding a new event &times; cache mapping is a one-line entry in
 * {@link #EVICTIONS} plus a class on the {@link EventListener#classes()} array
 * &mdash; no new method body.</p>
 */
@Component
public class CacheEvictionListener {

    private static final Logger LOG = LoggerFactory.getLogger(CacheEvictionListener.class);

    private static final String SPEND_CACHE = "spend";
    private static final String APPLY_NOW_CACHE = "envoy-apply-now";
    private static final String APPLY_NOW_STAT_CACHE = "envoy-apply-now-stat";

    private static final Map<Class<?>, Set<String>> EVICTIONS = Map.of(
            ServiceRecordCreated.class, Set.of(SPEND_CACHE),
            JobPostingScored.class, Set.of(APPLY_NOW_CACHE, APPLY_NOW_STAT_CACHE),
            PostingMarkedApplied.class, Set.of(APPLY_NOW_STAT_CACHE),
            PostingDismissed.class, Set.of(APPLY_NOW_STAT_CACHE));

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
     * Dispatches an incoming domain event by clearing each cache registered for
     * its concrete type. Unregistered events are a no-op so the dispatcher
     * stays safe even if a future {@link EventListener#classes()} entry is
     * added without a matching {@link #EVICTIONS} entry.
     *
     * @param event the domain event
     */
    @EventListener(classes = {
            ServiceRecordCreated.class,
            JobPostingScored.class,
            PostingMarkedApplied.class,
            PostingDismissed.class
    })
    public void onDomainEvent(Object event) {
        Set<String> caches = EVICTIONS.get(event.getClass());
        if (caches == null) {
            return;
        }
        LOG.debug("Evicting caches {} after {}", caches, event.getClass().getSimpleName());
        caches.forEach(this::evict);
    }

    private void evict(String cacheName) {
        var cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }
}
