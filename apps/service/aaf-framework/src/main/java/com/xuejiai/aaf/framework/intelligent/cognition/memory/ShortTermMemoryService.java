/**
 * 短期记忆服务（Redis 实现）。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.cognition.memory;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.util.JsonUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 短期记忆：租户、用户和会话三级隔离的上下文缓存，TTL 自动过期。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortTermMemoryService {

    private static final String KEY_PREFIX = "memory:short:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(2);
    private static final int MAX_MESSAGES = 50;

    private final StringRedisTemplate redisTemplate;

    /** 追加消息到对话上下文。 */
    public void append(String tenantId, Long userId, String conversationId, MemoryMessage message) {
        var key = key(tenantId, userId, conversationId);
        try {
            var json = JsonUtils.toJsonString(message);
            redisTemplate.opsForList().rightPush(key, json);
            redisTemplate.opsForList().trim(key, -MAX_MESSAGES, -1);
            redisTemplate.expire(key, DEFAULT_TTL);
        } catch (Exception e) {
            log.warn("短期记忆写入失败: {}", e.getMessage());
        }
    }

    /** 获取对话上下文（最近 N 条）。 */
    public List<MemoryMessage> getContext(
            String tenantId, Long userId, String conversationId, int limit) {
        if (limit <= 0) return List.of();
        var items =
                redisTemplate.opsForList().range(key(tenantId, userId, conversationId), -limit, -1);
        if (items == null || items.isEmpty()) return List.of();
        return items.stream().map(this::deserialize).toList();
    }

    /** 获取全部对话上下文。 */
    public List<MemoryMessage> getAll(String tenantId, Long userId, String conversationId) {
        return getContext(tenantId, userId, conversationId, MAX_MESSAGES);
    }

    /** 清除对话上下文。 */
    public void clear(String tenantId, Long userId, String conversationId) {
        redisTemplate.delete(key(tenantId, userId, conversationId));
    }

    /** 刷新 TTL。 */
    public void touch(String tenantId, Long userId, String conversationId) {
        redisTemplate.expire(key(tenantId, userId, conversationId), DEFAULT_TTL);
    }

    private String key(String tenantId, Long userId, String conversationId) {
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(userId, "userId 不能为空");
        Objects.requireNonNull(conversationId, "conversationId 不能为空");
        if (tenantId.isBlank() || conversationId.isBlank()) {
            throw new IllegalArgumentException("tenantId 和 conversationId 不能为空白");
        }
        return "%s%s:%s:%s".formatted(KEY_PREFIX, tenantId, userId, conversationId);
    }

    private MemoryMessage deserialize(String json) {
        try {
            return JsonUtils.parseObject(json, MemoryMessage.class);
        } catch (Exception e) {
            log.warn("记忆反序列化失败: {}", e.getMessage());
            return new MemoryMessage("system", "error", null);
        }
    }
}
