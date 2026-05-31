/**
 * 权限缓存服务——Redis 缓存用户权限列表，减少 DB 查询。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.security.cache;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.security.access.PermissionVersionService;

import lombok.RequiredArgsConstructor;

/** 用户权限缓存（Redis Set，TTL 5 分钟）。 */
@Service
@RequiredArgsConstructor
public class PermissionCacheService {

    private static final String KEY_PREFIX = "permission:user:";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;
    private final PermissionVersionService versionService;

    /**
     * 获取用户权限编码列表（优先缓存）
     *
     * @param userId 用户编号
     * @return 权限编码集合，缓存未命中返回 null（调用方需查 DB 并回填）
     */
    public Set<String> getPermissions(Long userId) {
        var key = key(userId);
        var members = redisTemplate.opsForSet().members(key);
        if (members == null || members.isEmpty()) {
            return null;
        }
        return members;
    }

    /**
     * 回填用户权限缓存
     *
     * @param userId 用户编号
     * @param permissions 权限编码列表
     */
    public void putPermissions(Long userId, List<String> permissions) {
        var key = key(userId);
        if (permissions.isEmpty()) {
            // 空权限也缓存，防止缓存穿透
            redisTemplate.opsForSet().add(key, "__EMPTY__");
        } else {
            redisTemplate.opsForSet().add(key, permissions.toArray(String[]::new));
        }
        redisTemplate.expire(key, TTL);
    }

    /**
     * 失效指定用户权限缓存
     *
     * @param userId 用户编号
     */
    public void evict(Long userId) {
        redisTemplate.delete(key(userId));
    }

    /** 失效所有用户权限缓存 */
    public void evictAll() {
        var keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId + ":" + versionService.permissionVersion();
    }
}
