package com.xuejiai.aaf.framework.security;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 权限缓存服务——基于 Redis 缓存用户权限列表，TTL 5 分钟。
 *
 * <p>缓存 key 格式：{@code permission:user:{userId}}，值为逗号分隔的权限标识列表。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionCacheService {

    private static final String KEY_PREFIX = "permission:user:";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;
    private final PermissionLoader permissionLoader;

    /**
     * 获取用户权限列表，优先从缓存读取，未命中则查 DB 并回填缓存。
     *
     * @param userId 用户 ID
     * @return 权限标识列表
     */
    public List<String> getPermissions(Long userId) {
        var key = KEY_PREFIX + userId;
        var cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return cached.isEmpty() ? List.of() : List.of(cached.split(","));
        }
        // 未命中，查 DB
        var permissions = permissionLoader.loadPermissions(userId);
        var value = String.join(",", permissions);
        redisTemplate.opsForValue().set(key, value, TTL);
        return permissions;
    }

    /**
     * 失效指定用户的权限缓存。
     *
     * @param userId 用户 ID
     */
    public void evict(Long userId) {
        redisTemplate.delete(KEY_PREFIX + userId);
        log.debug("权限缓存已失效: userId={}", userId);
    }

    /** 失效所有用户的权限缓存。 */
    public void evictAll() {
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.debug("权限缓存全量失效: count={}", keys.size());
        }
    }

    /**
     * 权限加载器——框架层 SPI，由业务层实现从 DB 加载用户权限。
     */
    public interface PermissionLoader {

        /**
         * 从数据库加载用户权限标识列表。
         *
         * @param userId 用户 ID
         * @return 权限标识列表
         */
        List<String> loadPermissions(Long userId);
    }
}
