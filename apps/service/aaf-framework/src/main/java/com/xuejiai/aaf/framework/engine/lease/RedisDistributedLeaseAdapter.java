package com.xuejiai.aaf.framework.engine.lease;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** Redis 分布式租约适配器；counter key 永不回退，确保 fencing token 单调递增。 */
public final class RedisDistributedLeaseAdapter implements DistributedLeasePort {

    private static final DefaultRedisScript<Long> ACQUIRE =
            new DefaultRedisScript<>(
                    """
            local current = redis.call('GET', KEYS[1])
            if not current then
                local token = redis.call('INCR', KEYS[2])
                local value = ARGV[1] .. '|' .. token
                local ok = redis.call('SET', KEYS[1], value, 'PX', ARGV[2], 'NX')
                if ok then return token end
                return 0
            end
            local prefix = ARGV[1] .. '|'
            if string.sub(current, 1, string.len(prefix)) == prefix then
                redis.call('PEXPIRE', KEYS[1], ARGV[2])
                return tonumber(string.sub(current, string.len(prefix) + 1))
            end
            return 0
            """,
                    Long.class);
    private static final DefaultRedisScript<Long> RENEW =
            new DefaultRedisScript<>(
                    """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            return 0
            """,
                    Long.class);
    private static final DefaultRedisScript<Long> PREEMPT =
            new DefaultRedisScript<>(
                    """
            local token = redis.call('INCR', KEYS[2])
            local value = ARGV[1] .. '|' .. token
            redis.call('SET', KEYS[1], value, 'PX', ARGV[2])
            return token
            """,
                    Long.class);
    private static final DefaultRedisScript<Long> RELEASE =
            new DefaultRedisScript<>(
                    """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """,
                    Long.class);
    private static final DefaultRedisScript<Long> VALIDATE =
            new DefaultRedisScript<>(
                    """
            if redis.call('GET', KEYS[1]) == ARGV[1] and redis.call('PTTL', KEYS[1]) > 0 then
                return 1
            end
            return 0
            """,
                    Long.class);

    private final StringRedisTemplate redis;

    public RedisDistributedLeaseAdapter(StringRedisTemplate redis) {
        this.redis = Objects.requireNonNull(redis, "redis 不能为空");
    }

    @Override
    public Optional<Lease> acquire(String key, String ownerId, Duration ttl) {
        requireKey(key);
        requireOwnerId(ownerId);
        var ttlMillis = requireTtlMillis(ttl);
        var token =
                redis.execute(
                        ACQUIRE, List.of(key, counterKey(key)), ownerId, Long.toString(ttlMillis));
        return token == null || token < 1
                ? Optional.empty()
                : Optional.of(new Lease(key, ownerId, token, Instant.now().plusMillis(ttlMillis)));
    }

    @Override
    public Optional<Lease> renew(Lease current, Duration ttl) {
        requireLease(current);
        var ttlMillis = requireTtlMillis(ttl);
        var renewed =
                redis.execute(
                        RENEW, List.of(current.key()), value(current), Long.toString(ttlMillis));
        return renewed != null && renewed == 1
                ? Optional.of(
                        new Lease(
                                current.key(),
                                current.ownerId(),
                                current.fencingToken(),
                                Instant.now().plusMillis(ttlMillis)))
                : Optional.empty();
    }

    @Override
    public void release(Lease lease) {
        releaseIfCurrent(lease);
    }

    @Override
    public void requireCurrent(Lease lease) {
        requireLease(lease);
        var valid = redis.execute(VALIDATE, List.of(lease.key()), value(lease));
        if (valid == null || valid != 1) {
            throw new IllegalStateException(
                    "distributed lease 已失效或 fencing token 过期: " + lease.key());
        }
    }

    /** 原子抢占当前租约，供需要显式接管语义的上层适配器使用。 */
    public Lease preempt(String key, String ownerId, Duration ttl) {
        requireKey(key);
        requireOwnerId(ownerId);
        var ttlMillis = requireTtlMillis(ttl);
        var token =
                redis.execute(
                        PREEMPT, List.of(key, counterKey(key)), ownerId, Long.toString(ttlMillis));
        if (token == null || token < 1) {
            throw new IllegalStateException("抢占 distributed lease 失败: " + key);
        }
        return new Lease(key, ownerId, token, Instant.now().plusMillis(ttlMillis));
    }

    /** 读取当前有效租约，供需要诊断当前持有者的上层适配器使用。 */
    public Optional<Lease> inspect(String key) {
        requireKey(key);
        var current = redis.opsForValue().get(key);
        var ttlMillis = redis.getExpire(key, TimeUnit.MILLISECONDS);
        if (current == null || ttlMillis == null || ttlMillis < 1) {
            return Optional.empty();
        }
        var separator = current.lastIndexOf('|');
        if (separator < 1) {
            throw new IllegalStateException("distributed lease value 损坏: " + key);
        }
        return Optional.of(
                new Lease(
                        key,
                        current.substring(0, separator),
                        Long.parseLong(current.substring(separator + 1)),
                        Instant.now().plusMillis(ttlMillis)));
    }

    /** CAS 释放当前租约，并返回是否确实删除。 */
    public boolean releaseIfCurrent(Lease lease) {
        requireLease(lease);
        var released = redis.execute(RELEASE, List.of(lease.key()), value(lease));
        return released != null && released == 1;
    }

    private static void requireLease(Lease lease) {
        Objects.requireNonNull(lease, "lease 不能为空");
        requireKey(lease.key());
        requireOwnerId(lease.ownerId());
        if (lease.fencingToken() < 1) {
            throw new IllegalArgumentException("fencingToken 必须大于 0");
        }
        Objects.requireNonNull(lease.expiresAt(), "expiresAt 不能为空");
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("lease key 不能为空白");
        }
    }

    private static void requireOwnerId(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId 不能为空白");
        }
    }

    private static long requireTtlMillis(Duration ttl) {
        Objects.requireNonNull(ttl, "ttl 不能为空");
        var ttlMillis = ttl.toMillis();
        if (ttlMillis < 1) {
            throw new IllegalArgumentException("lease ttl 必须至少为 1ms");
        }
        return ttlMillis;
    }

    private static String value(Lease lease) {
        return lease.ownerId() + '|' + lease.fencingToken();
    }

    private static String counterKey(String key) {
        return key + ":fencing-token";
    }
}
