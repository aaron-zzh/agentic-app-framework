package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.lease;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/** Redis conversation lease；counter key 永不回退，确保 fencing token 单调递增。 */
public final class RedisConversationLeaseAdapter implements ConversationLeasePort {

    private static final DefaultRedisScript<Long> ACQUIRE = new DefaultRedisScript<>("""
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
            """, Long.class);
    private static final DefaultRedisScript<Long> RENEW = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            return 0
            """, Long.class);
    private static final DefaultRedisScript<Long> PREEMPT = new DefaultRedisScript<>("""
            local token = redis.call('INCR', KEYS[2])
            local value = ARGV[1] .. '|' .. token
            redis.call('SET', KEYS[1], value, 'PX', ARGV[2])
            return token
            """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);
    private static final DefaultRedisScript<Long> VALIDATE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] and redis.call('PTTL', KEYS[1]) > 0 then
                return 1
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;

    public RedisConversationLeaseAdapter(StringRedisTemplate redis) {
        this.redis = Objects.requireNonNull(redis, "redis 不能为空");
    }

    @Override
    public Optional<Lease> acquire(
            TenantId tenantId, ConversationId conversationId, String ownerId, Duration ttl) {
        requireTtl(ttl);
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId 不能为空白");
        }
        var token = redis.execute(
                ACQUIRE,
                List.of(leaseKey(tenantId, conversationId), counterKey(tenantId, conversationId)),
                ownerId,
                Long.toString(ttl.toMillis()));
        return token == null || token < 1
                ? Optional.empty()
                : Optional.of(new Lease(tenantId, conversationId, ownerId, token, Instant.now().plus(ttl)));
    }

    @Override
    public Optional<Lease> renew(Lease lease, Duration ttl) {
        Objects.requireNonNull(lease, "lease 不能为空");
        requireTtl(ttl);
        var renewed = redis.execute(
                RENEW,
                List.of(leaseKey(lease.tenantId(), lease.conversationId())),
                value(lease),
                Long.toString(ttl.toMillis()));
        return renewed != null && renewed == 1
                ? Optional.of(new Lease(
                        lease.tenantId(), lease.conversationId(), lease.ownerId(),
                        lease.fencingToken(), Instant.now().plus(ttl)))
                : Optional.empty();
    }

    @Override
    public Lease preempt(
            TenantId tenantId, ConversationId conversationId, String ownerId, Duration ttl) {
        requireTtl(ttl);
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId 不能为空白");
        }
        var token = redis.execute(
                PREEMPT,
                List.of(leaseKey(tenantId, conversationId), counterKey(tenantId, conversationId)),
                ownerId,
                Long.toString(ttl.toMillis()));
        if (token == null || token < 1) {
            throw new IllegalStateException("抢占 conversation lease 失败");
        }
        return new Lease(tenantId, conversationId, ownerId, token, Instant.now().plus(ttl));
    }

    @Override
    public boolean release(Lease lease) {
        Objects.requireNonNull(lease, "lease 不能为空");
        var released = redis.execute(
                RELEASE,
                List.of(leaseKey(lease.tenantId(), lease.conversationId())),
                value(lease));
        return released != null && released == 1;
    }

    @Override
    public Optional<Lease> inspect(TenantId tenantId, ConversationId conversationId) {
        var key = leaseKey(tenantId, conversationId);
        var current = redis.opsForValue().get(key);
        var ttl = redis.getExpire(key);
        if (current == null || ttl == null || ttl < 0) return Optional.empty();
        var separator = current.lastIndexOf('|');
        if (separator < 1) throw new IllegalStateException("conversation lease value 损坏");
        return Optional.of(new Lease(
                tenantId,
                conversationId,
                current.substring(0, separator),
                Long.parseLong(current.substring(separator + 1)),
                Instant.now().plusSeconds(ttl)));
    }

    @Override
    public void requireCurrent(Lease lease) {
        Objects.requireNonNull(lease, "lease 不能为空");
        var valid = redis.execute(
                VALIDATE,
                List.of(leaseKey(lease.tenantId(), lease.conversationId())),
                value(lease));
        if (valid == null || valid != 1) {
            throw new IllegalStateException(
                    "conversation lease 已失效或 fencing token 过期: " + lease.conversationId().value());
        }
    }

    private static void requireTtl(Duration ttl) {
        Objects.requireNonNull(ttl, "ttl 不能为空");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("lease ttl 必须大于 0");
        }
    }

    private static String value(Lease lease) {
        return lease.ownerId() + '|' + lease.fencingToken();
    }

    private static String leaseKey(TenantId tenantId, ConversationId conversationId) {
        return "aaf:conversation:lease:" + tenantId.value() + ':' + conversationId.value();
    }

    private static String counterKey(TenantId tenantId, ConversationId conversationId) {
        return "aaf:conversation:fence:" + tenantId.value() + ':' + conversationId.value();
    }
}
