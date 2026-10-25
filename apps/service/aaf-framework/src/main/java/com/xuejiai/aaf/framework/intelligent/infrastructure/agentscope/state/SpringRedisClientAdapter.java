package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.state;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import io.agentscope.extensions.redis.state.RedisClientAdapter;

/**
 * 复用 Spring 管理连接的 AgentScope Redis 客户端适配器。
 *
 * <p>AgentScope 2.0.4 通过 {@link RedisClientAdapter#evalScript(String, List, List)}
 * 将状态值、版本号和会话键注册表在同一 Lua 脚本中原子更新；本适配器直接委托 Spring Data Redis 执行该脚本，避免另起 Jedis/Lettuce 连接池。所有读操作把
 * Spring 的 null 返回归一化为空集合。
 */
public final class SpringRedisClientAdapter implements RedisClientAdapter {

    /** SCAN 每批返回的键数上限；批太小会增加往返次数，太大会拉长单批阻塞时间。 */
    private static final int SCAN_BATCH_SIZE = 500;

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

    /**
     * 按模式查键：用 {@code SCAN} 游标而非 {@code KEYS}（RQ-13）。
     *
     * <p>{@code KEYS} 在 Redis 单线程上一次性遍历整个 keyspace，键数上万时会阻塞所有其他命令——包括本进程的状态读写。 {@code SCAN} 分批返回、每批
     * O(count)，代价是不保证快照一致性（遍历期间新增/删除的键可能漏报或重复），这对 "列出会话键"这类用途可以接受。
     */
    @Override
    public Set<String> findKeysByPattern(String pattern) {
        var options = ScanOptions.scanOptions().match(pattern).count(SCAN_BATCH_SIZE).build();
        var keys = new LinkedHashSet<String>();
        try (var cursor = redisTemplate.scan(options)) {
            cursor.forEachRemaining(keys::add);
        }
        return keys;
    }

    @Override
    public long evalScript(String script, List<String> keys, List<String> args) {
        var redisScript = new DefaultRedisScript<>(script, Long.class);
        var result = redisTemplate.execute(redisScript, keys, args.toArray());
        if (result == null) {
            throw new IllegalStateException("Unexpected Lua script result: null");
        }
        return result;
    }

    @Override
    public void close() {
        // 连接生命周期由 Spring 管理。
    }
}
