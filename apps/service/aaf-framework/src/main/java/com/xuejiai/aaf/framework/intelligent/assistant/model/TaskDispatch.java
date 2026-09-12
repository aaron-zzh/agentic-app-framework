package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.time.Instant;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/** durable 调度事实；lease、generation 与 fencingToken 的唯一当前真理。 */
public record TaskDispatch(
        String dispatchId,
        TenantId tenantId,
        ExecutionId executionId,
        Status status,
        Instant nextRunAt,
        String leaseOwner,
        Instant leaseUntil,
        long generation,
        long fencingToken,
        int deliveryAttempts,
        String lastError,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public TaskDispatch {
        if (dispatchId == null || dispatchId.isBlank()) {
            throw new IllegalArgumentException("dispatchId 不能为空白");
        }
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(executionId, "executionId 不能为空");
        Objects.requireNonNull(status, "status 不能为空");
        Objects.requireNonNull(nextRunAt, "nextRunAt 不能为空");
        Objects.requireNonNull(createdAt, "createdAt 不能为空");
        Objects.requireNonNull(updatedAt, "updatedAt 不能为空");
        if (generation < 1 || fencingToken < 1 || deliveryAttempts < 0 || version < 0) {
            throw new IllegalArgumentException("generation/fence 必须为正数，投递次数/version 不能为负数");
        }
        if (status == Status.CLAIMED
                && (leaseOwner == null || leaseOwner.isBlank() || leaseUntil == null)) {
            throw new IllegalArgumentException("CLAIMED dispatch 必须持有完整租约");
        }
        if (status != Status.CLAIMED && (leaseOwner != null || leaseUntil != null)) {
            throw new IllegalArgumentException("非 CLAIMED dispatch 不得保留租约");
        }
    }

    public boolean active() {
        return status == Status.PENDING || status == Status.CLAIMED;
    }

    public boolean accepts(
            String expectedDispatchId,
            long expectedGeneration,
            long expectedFence,
            String expectedLeaseOwner,
            Instant now) {
        return status == Status.CLAIMED
                && dispatchId.equals(expectedDispatchId)
                && generation == expectedGeneration
                && fencingToken == expectedFence
                && Objects.equals(leaseOwner, expectedLeaseOwner)
                && leaseUntil != null
                && leaseUntil.isAfter(now);
    }

    public enum Status {
        PENDING,
        CLAIMED,
        DONE,
        CANCELED
    }
}
