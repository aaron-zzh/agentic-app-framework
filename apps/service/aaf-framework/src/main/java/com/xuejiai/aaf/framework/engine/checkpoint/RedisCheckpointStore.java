package com.xuejiai.aaf.framework.engine.checkpoint;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于 Redis Hash 的 Checkpoint 存储实现。
 *
 * <p>存储结构：key = "checkpoint:{ownerId}"，field = checkpointId，value = JSON。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisCheckpointStore implements CheckpointStore {

    private static final String KEY_PREFIX = "checkpoint:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void save(CheckpointEntry entry) {
        try {
            var json = objectMapper.writeValueAsString(entry);
            var key = KEY_PREFIX + entry.ownerId();
            redisTemplate.opsForHash().put(key, entry.id(), json);
            // 设置 TTL
            var ttl = Duration.between(Instant.now(), entry.expiresAt());
            if (!ttl.isNegative()) {
                redisTemplate.expire(key, ttl);
            }
        } catch (Exception e) {
            log.error("Checkpoint 保存失败: {}", entry.id(), e);
        }
    }

    @Override
    public Optional<CheckpointEntry> load(String checkpointId) {
        // 需要遍历查找，因为 checkpointId 是 field 而非 key
        var keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (keys == null) {
            return Optional.empty();
        }
        for (var key : keys) {
            var json = (String) redisTemplate.opsForHash().get(key, checkpointId);
            if (json != null) {
                return deserialize(json);
            }
        }
        return Optional.empty();
    }

    @Override
    public void delete(String checkpointId) {
        var keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (keys == null) return;
        for (var key : keys) {
            redisTemplate.opsForHash().delete(key, checkpointId);
        }
    }

    @Override
    public List<CheckpointEntry> listByOwner(String ownerId) {
        var key = KEY_PREFIX + ownerId;
        var entries = redisTemplate.opsForHash().values(key);
        return entries.stream()
                .map(v -> deserialize((String) v))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    private Optional<CheckpointEntry> deserialize(String json) {
        try {
            return Optional.of(objectMapper.readValue(json, CheckpointEntry.class));
        } catch (Exception e) {
            log.warn("Checkpoint 反序列化失败", e);
            return Optional.empty();
        }
    }
}
