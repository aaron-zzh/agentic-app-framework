package com.xuejiai.aaf.module.system.role.service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** L3 记录规则两级缓存：请求内 ThreadLocal + Redis JSON。 */
@Component
@RequiredArgsConstructor
public class DataAccessRuleCache {

    public static final String NO_RULE = "__NO_RULE__";

    private static final String KEY_PREFIX = "data_rule:";
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final ThreadLocal<Map<String, String>> REQUEST_CACHE =
            ThreadLocal.withInitial(HashMap::new);

    private final StringRedisTemplate redisTemplate;

    public String get(String entitySlug, Long userId, String version) {
        var key = key(entitySlug, userId, version);
        var local = REQUEST_CACHE.get();
        if (local.containsKey(key)) {
            return local.get(key);
        }
        var value = redisTemplate.opsForValue().get(key);
        if (value != null) {
            local.put(key, value);
        }
        return value;
    }

    public void put(String entitySlug, Long userId, String version, String value) {
        var key = key(entitySlug, userId, version);
        REQUEST_CACHE.get().put(key, value);
        redisTemplate.opsForValue().set(key, value, TTL);
    }

    public void evictEntity(String entitySlug) {
        var keys = redisTemplate.keys(KEY_PREFIX + entitySlug + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        clearRequestCache();
    }

    public static void clearRequestCache() {
        REQUEST_CACHE.remove();
    }

    private String key(String entitySlug, Long userId, String version) {
        return KEY_PREFIX + entitySlug + ":" + userId + ":" + version;
    }
}
