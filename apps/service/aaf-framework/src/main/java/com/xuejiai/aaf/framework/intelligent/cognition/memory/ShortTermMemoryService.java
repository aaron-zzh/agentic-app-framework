/**
 * 短期记忆服务（Redis 实现）。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.cognition.memory;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.util.JsonUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 短期记忆：对话级上下文缓存，TTL 自动过期。 存储在 Redis 中，key 格式：memory:short:{conversationId} */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortTermMemoryService {

    private static final String KEY_PREFIX = "memory:short:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(2);
    private static final int MAX_MESSAGES = 50;

    private final StringRedisTemplate redisTemplate;

    /** 追加消息到对话上下文 */
    public void append(String conversationId, MemoryMessage message) {
        var key = KEY_PREFIX + conversationId;
        try {
            var json = JsonUtils.toJsonString(message);
            redisTemplate.opsForList().rightPush(key, json);
            // 保持滑动窗口
            redisTemplate.opsForList().trim(key, -MAX_MESSAGES, -1);
            redisTemplate.expire(key, DEFAULT_TTL);
        } catch (Exception e) {
            log.warn("短期记忆写入失败: {}", e.getMessage());
        }
    }

    /** 获取对话上下文（最近 N 条） */
    public List<MemoryMessage> getContext(String conversationId, int limit) {
        var key = KEY_PREFIX + conversationId;
        var items = redisTemplate.opsForList().range(key, -limit, -1);
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream().map(this::deserialize).toList();
    }

    /** 获取全部对话上下文 */
    public List<MemoryMessage> getAll(String conversationId) {
        return getContext(conversationId, MAX_MESSAGES);
    }

    /** 清除对话上下文 */
    public void clear(String conversationId) {
        redisTemplate.delete(KEY_PREFIX + conversationId);
    }

    /** 刷新 TTL */
    public void touch(String conversationId) {
        redisTemplate.expire(KEY_PREFIX + conversationId, DEFAULT_TTL);
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
