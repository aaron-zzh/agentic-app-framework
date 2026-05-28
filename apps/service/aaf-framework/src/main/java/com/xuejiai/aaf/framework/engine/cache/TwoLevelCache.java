package com.xuejiai.aaf.framework.engine.cache;

import java.time.Duration;
import java.util.function.Function;

import org.springframework.data.redis.core.StringRedisTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import lombok.extern.slf4j.Slf4j;

/**
 * 二级缓存：本地 Caffeine + 远程 Redis。
 *
 * <p>读取顺序：本地 → Redis → loader；写入同时写两级。
 */
@Slf4j
public class TwoLevelCache<K, V> {

    private final Cache<K, V> localCache;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String name;
    private final Class<V> type;
    private final Duration redisTtl;

    public TwoLevelCache(String name, Class<V> type, int maxSize,
                         Duration localTtl, Duration redisTtl,
                         StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.name = name;
        this.type = type;
        this.redisTtl = redisTtl;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.localCache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(localTtl)
                .build();
    }

    /** 获取缓存值，未命中时调用 loader 加载 */
    public V get(K key, Function<K, V> loader) {
        // 本地命中
        var local = localCache.getIfPresent(key);
        if (local != null) {
            return local;
        }
        // Redis 命中
        var redisValue = getFromRedis(key);
        if (redisValue != null) {
            localCache.put(key, redisValue);
            return redisValue;
        }
        // 都未命中，调 loader
        var loaded = loader.apply(key);
        if (loaded != null) {
            put(key, loaded);
        }
        return loaded;
    }

    /** 写入两级缓存 */
    public void put(K key, V value) {
        localCache.put(key, value);
        putToRedis(key, value);
    }

    /** 删除指定 key */
    public void invalidate(K key) {
        localCache.invalidate(key);
        redisTemplate.delete(redisKey(key));
    }

    /** 清空全部 */
    public void invalidateAll() {
        localCache.invalidateAll();
        var keys = redisTemplate.keys(name + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private String redisKey(K key) {
        return name + ":" + key;
    }

    private V getFromRedis(K key) {
        try {
            var json = redisTemplate.opsForValue().get(redisKey(key));
            if (json != null) {
                return objectMapper.readValue(json, type);
            }
        } catch (Exception e) {
            log.warn("Redis 缓存读取失败: cache={}, key={}", name, key, e);
        }
        return null;
    }

    private void putToRedis(K key, V value) {
        try {
            var json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(redisKey(key), json, redisTtl);
        } catch (Exception e) {
            log.warn("Redis 缓存写入失败: cache={}, key={}", name, key, e);
        }
    }
}
