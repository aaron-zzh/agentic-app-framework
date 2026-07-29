package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.state;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;

import io.agentscope.extensions.redis.state.RedisClientAdapter;

/** 复用 Spring 管理连接的 AgentScope Redis 客户端适配器。 */
public final class SpringRedisClientAdapter implements RedisClientAdapter {

    private final StringRedisTemplate redisTemplate;

    public SpringRedisClientAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void set(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }

    @Override
    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public void rightPushList(String key, String value) {
        redisTemplate.opsForList().rightPush(key, value);
    }

    @Override
    public List<String> rangeList(String key, long start, long end) {
        var values = redisTemplate.opsForList().range(key, start, end);
        return values == null ? List.of() : values;
    }

    @Override
    public long getListLength(String key) {
        var size = redisTemplate.opsForList().size(key);
        return size == null ? 0 : size;
    }

    @Override
    public void deleteKeys(String... keys) {
        if (keys.length > 0) {
            redisTemplate.delete(Arrays.asList(keys));
        }
    }

    @Override
    public void addToSet(String key, String member) {
        redisTemplate.opsForSet().add(key, member);
    }

    @Override
    public Set<String> getSetMembers(String key) {
        var members = redisTemplate.opsForSet().members(key);
        return members == null ? Set.of() : members;
    }

    @Override
    public long getSetSize(String key) {
        var size = redisTemplate.opsForSet().size(key);
        return size == null ? 0 : size;
    }

    @Override
    public boolean keyExists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    @Override
    public Set<String> findKeysByPattern(String pattern) {
        var keys = redisTemplate.keys(pattern);
        return keys == null ? Set.of() : keys;
    }

    @Override
    public void close() {
        // 连接生命周期由 Spring 管理。
    }
}
