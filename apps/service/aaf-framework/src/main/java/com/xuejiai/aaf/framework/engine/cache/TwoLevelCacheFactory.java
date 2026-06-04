package com.xuejiai.aaf.framework.engine.cache;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/** 二级缓存工厂——创建并管理 TwoLevelCache 实例。 */
@Component
@RequiredArgsConstructor
public class TwoLevelCacheFactory {

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;

    /** 已创建的缓存实例注册表 */
    private final Map<String, TwoLevelCache<?, ?>> registry = new ConcurrentHashMap<>();

    /** 创建二级缓存实例 */
    public <V> TwoLevelCache<Long, V> create(
            String name, Class<V> type, int maxSize, Duration localTtl, Duration redisTtl) {
        var cache =
                new TwoLevelCache<Long, V>(
                        name, type, maxSize, localTtl, redisTtl, redisTemplate, jsonMapper);
        registry.put(name, cache);
        return cache;
    }

    /** 根据名称获取已注册的缓存实例 */
    @SuppressWarnings("unchecked")
    public <K, V> TwoLevelCache<K, V> getCache(String name) {
        return (TwoLevelCache<K, V>) registry.get(name);
    }
}
