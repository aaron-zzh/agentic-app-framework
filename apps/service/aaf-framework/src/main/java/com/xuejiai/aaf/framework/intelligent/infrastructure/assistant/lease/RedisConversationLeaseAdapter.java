package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.lease;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import com.xuejiai.aaf.framework.engine.lease.DistributedLeasePort;
import com.xuejiai.aaf.framework.engine.lease.RedisDistributedLeaseAdapter;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/** 将 conversation 标识映射到通用分布式租约 key。 */
public final class RedisConversationLeaseAdapter implements ConversationLeasePort {

    private final RedisDistributedLeaseAdapter leases;

    public RedisConversationLeaseAdapter(RedisDistributedLeaseAdapter leases) {
        this.leases = Objects.requireNonNull(leases, "leases 不能为空");
    }

    @Override
    public Optional<Lease> acquire(
            TenantId tenantId, ConversationId conversationId, String ownerId, Duration ttl) {
        return leases.acquire(key(tenantId, conversationId), ownerId, ttl)
                .map(lease -> toConversationLease(tenantId, conversationId, lease));
    }

    @Override
    public Optional<Lease> renew(Lease lease, Duration ttl) {
        Objects.requireNonNull(lease, "lease 不能为空");
        return leases.renew(toDistributedLease(lease), ttl)
                .map(
                        renewed ->
                                toConversationLease(
                                        lease.tenantId(), lease.conversationId(), renewed));
    }

    @Override
    public Lease preempt(
            TenantId tenantId, ConversationId conversationId, String ownerId, Duration ttl) {
        var lease = leases.preempt(key(tenantId, conversationId), ownerId, ttl);
        return toConversationLease(tenantId, conversationId, lease);
    }

    @Override
    public boolean release(Lease lease) {
        Objects.requireNonNull(lease, "lease 不能为空");
        return leases.releaseIfCurrent(toDistributedLease(lease));
    }

    @Override
    public Optional<Lease> inspect(TenantId tenantId, ConversationId conversationId) {
        return leases.inspect(key(tenantId, conversationId))
                .map(lease -> toConversationLease(tenantId, conversationId, lease));
    }

    @Override
    public void requireCurrent(Lease lease) {
        Objects.requireNonNull(lease, "lease 不能为空");
        leases.requireCurrent(toDistributedLease(lease));
    }

    private static Lease toConversationLease(
            TenantId tenantId, ConversationId conversationId, DistributedLeasePort.Lease lease) {
        return new Lease(
                tenantId, conversationId, lease.ownerId(), lease.fencingToken(), lease.expiresAt());
    }

    private static DistributedLeasePort.Lease toDistributedLease(Lease lease) {
        return new DistributedLeasePort.Lease(
                key(lease.tenantId(), lease.conversationId()),
                lease.ownerId(),
                lease.fencingToken(),
                lease.expiresAt());
    }

    private static String key(TenantId tenantId, ConversationId conversationId) {
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(conversationId, "conversationId 不能为空");
        return tenantId.value() + ':' + conversationId.value();
    }
}
