package com.xuejiai.aaf.framework.engine.cache;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 缓存失效事件监听器——根据事件刷新对应缓存实例。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheInvalidationListener {

    private final TwoLevelCacheFactory cacheFactory;

    @EventListener
    @SuppressWarnings("unchecked")
    public void onCacheInvalidation(CacheInvalidationEvent event) {
        TwoLevelCache<Object, Object> cache = cacheFactory.getCache(event.cacheName());
        if (cache == null) {
            log.debug("未找到缓存实例: {}", event.cacheName());
            return;
        }
        if (event.key() == null) {
            cache.invalidateAll();
            log.info("全量刷新缓存: {}", event.cacheName());
        } else {
            cache.invalidate(event.key());
            log.debug("刷新缓存: {}:{}", event.cacheName(), event.key());
        }
    }
}
