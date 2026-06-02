package com.xuejiai.aaf.module.system.role.relation;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.security.access.PermissionVersionService;

import lombok.RequiredArgsConstructor;

/** ReBAC 判定缓存，正负结果均缓存，版本进入 key。 */
@Component
@RequiredArgsConstructor
public class RebacPermissionCache {

    private static final String KEY_PREFIX = "rebac:";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;
    private final PermissionVersionService versionService;

    public Boolean get(Long userId, String objectType, String objectId, String permission) {
        var value = redisTemplate.opsForValue().get(key(userId, objectType, objectId, permission));
        return value == null ? null : Boolean.valueOf(value);
    }

    public void put(
            Long userId, String objectType, String objectId, String permission, boolean allowed) {
        redisTemplate
                .opsForValue()
                .set(key(userId, objectType, objectId, permission), String.valueOf(allowed), TTL);
    }

    public void evictObject(String objectType, String objectId) {
        var keys = redisTemplate.keys(KEY_PREFIX + "*:" + objectType + ":" + objectId + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private String key(Long userId, String objectType, String objectId, String permission) {
        return "%s%s:%s:%s:%s:%s"
                .formatted(
                        KEY_PREFIX,
                        userId,
                        objectType,
                        objectId,
                        permission,
                        versionService.relationSchemaVersion());
    }
}
