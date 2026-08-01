package com.xuejiai.aaf.framework.engine.cache;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 缓存失效事件监听器——根据事件刷新对应缓存实例。
 *
 * <p>M50：本机（Caffeine + Redis）失效完成后，经 {@link CacheInvalidationBroadcaster} 广播到其他实例，
 * 让它们同步清掉本地副本；否则多实例部署下别的节点最长会用 LOCAL_TTL 内的旧配置。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheInvalidationListener {

    private final TwoLevelCacheFactory cacheFactory;
    private final CacheInvalidationBroadcaster broadcaster;

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
        broadcaster.broadcast(event.cacheName(), event.key());
    }
}
