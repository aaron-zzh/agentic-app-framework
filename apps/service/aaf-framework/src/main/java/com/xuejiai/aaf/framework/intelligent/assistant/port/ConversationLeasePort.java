package com.xuejiai.aaf.framework.intelligent.assistant.port;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/** 同一 conversation 的单 owner 租约和单调 fencing token 边界。 */
public interface ConversationLeasePort {

    Optional<Lease> acquire(TenantId tenantId, ConversationId conversationId, String ownerId, Duration ttl);

    Optional<Lease> renew(Lease lease, Duration ttl);

    /** 人工控制或恢复接管时原子替换 owner，并分配严格递增的新 fencing token。 */
    Lease preempt(TenantId tenantId, ConversationId conversationId, String ownerId, Duration ttl);

    boolean release(Lease lease);

    Optional<Lease> inspect(TenantId tenantId, ConversationId conversationId);

    void requireCurrent(Lease lease);

    record Lease(
            TenantId tenantId,
            ConversationId conversationId,
            String ownerId,
            long fencingToken,
            Instant expiresAt) {
        public Lease {
            Objects.requireNonNull(tenantId, "tenantId 不能为空");
            Objects.requireNonNull(conversationId, "conversationId 不能为空");
            if (ownerId == null || ownerId.isBlank()) {
                throw new IllegalArgumentException("lease ownerId 不能为空白");
            }
            if (fencingToken < 1) {
                throw new IllegalArgumentException("fencingToken 必须大于 0");
            }
            Objects.requireNonNull(expiresAt, "expiresAt 不能为空");
        }
    }
}
