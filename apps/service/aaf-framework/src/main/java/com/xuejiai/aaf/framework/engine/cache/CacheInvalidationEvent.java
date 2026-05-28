package com.xuejiai.aaf.framework.engine.cache;

/**
 * 缓存失效事件——通过 Spring 事件总线通知缓存刷新。
 *
 * @param cacheName 缓存名称
 * @param key       缓存 key，null 表示全量刷新
 */
public record CacheInvalidationEvent(String cacheName, Object key) {
}
