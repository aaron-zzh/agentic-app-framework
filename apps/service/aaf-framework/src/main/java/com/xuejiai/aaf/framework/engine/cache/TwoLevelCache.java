package com.xuejiai.aaf.framework.engine.cache;

import java.time.Duration;
import java.util.function.Function;

import org.springframework.data.redis.core.StringRedisTemplate;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * 二级缓存：本地 Caffeine + 远程 Redis。
 *
 * <p>读取顺序：本地 → Redis → loader；写入同时写两级。
 */
@Slf4j
public class TwoLevelCache<K, V> {

    private final Cache<K, V> localCache;
    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final String name;
    private final Class<V> type;
    private final Duration redisTtl;

    public TwoLevelCache(
            String name,
            Class<V> type,
            int maxSize,
            Duration localTtl,
            Duration redisTtl,
            StringRedisTemplate redisTemplate,
            JsonMapper jsonMapper) {
        this.name = name;
        this.type = type;
        this.redisTtl = redisTtl;
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
        this.localCache =
                Caffeine.newBuilder().maximumSize(maxSize).expireAfterWrite(localTtl).build();
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

    /** 删除指定 key（本机 + Redis） */
    public void invalidate(K key) {
        localCache.invalidate(key);
        redisTemplate.delete(redisKey(key));
    }

    /**
     * 只清本机本地缓存，不动 Redis（M50）。
     *
     * <p>用于接收其他实例广播的失效通知——Redis 侧已由发起实例删除，这里只需丢掉本机 Caffeine 副本， 避免重复删除与广播回环。
     */
    public void invalidateLocal(K key) {
        localCache.invalidate(key);
    }

    /** 只清空本机本地缓存，不动 Redis（M50：广播接收侧使用）。 */
    public void invalidateAllLocal() {
        localCache.invalidateAll();
    }

    /**
     * 清空全部。
     *
     * <p>m30：改用 SCAN 游标分批删除——原实现用 {@code KEYS name:*}，Redis 单线程执行 KEYS 会随 key 总量线性阻塞，
     * 生产环境上百万 key 时足以造成全实例卡顿。SCAN 分批返回、每批删除，不阻塞其他命令。
     */
    public void invalidateAll() {
        localCache.invalidateAll();
        var options =
                org.springframework.data.redis.core.ScanOptions.scanOptions()
                        .match(name + ":*")
                        .count(500)
                        .build();
        try (var cursor = redisTemplate.scan(options)) {
            var batch = new java.util.ArrayList<String>(500);
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() >= 500) {
                    redisTemplate.delete(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                redisTemplate.delete(batch);
            }
        }
    }

    private String redisKey(K key) {
        return name + ":" + key;
    }

    private V getFromRedis(K key) {
        try {
            var json = redisTemplate.opsForValue().get(redisKey(key));
            if (json != null) {
                return jsonMapper.readValue(json, type);
            }
        } catch (Exception e) {
            log.warn("Redis 缓存读取失败，清除脏数据回源: cache={}, key={}", name, key);
            redisTemplate.delete(redisKey(key));
        }
        return null;
    }

    private void putToRedis(K key, V value) {
        try {
            var json = jsonMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(redisKey(key), json, redisTtl);
        } catch (Exception e) {
            log.warn("Redis 缓存写入失败: cache={}, key={}", name, key, e);
        }
    }
}
