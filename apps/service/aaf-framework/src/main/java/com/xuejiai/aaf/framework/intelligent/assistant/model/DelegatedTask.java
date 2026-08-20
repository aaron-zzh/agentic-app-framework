package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/** PostgreSQL 持久化的委托任务事实。 */
public record DelegatedTask(
        TenantId tenantId,
        UserId userId,
        TaskId taskId,
        ConversationId conversationId,
        SessionId sessionId,
        ExecutionId executionId,
        ExecutionId parentExecutionId,
        Source source,
        int priority,
        Status status,
        Owner owner,
        ExecutionContract contract,
        BudgetUsage budgetUsage,
        int attempts,
        int consecutiveFailures,
        Instant nextRunAt,
        String leaseOwner,
        Instant leaseUntil,
        long fencingToken,
        Map<String, Object> checkpoint,
        Instant createdAt,
        Instant updatedAt) {

    public DelegatedTask {
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(userId, "userId 不能为空");
        Objects.requireNonNull(taskId, "taskId 不能为空");
        Objects.requireNonNull(conversationId, "conversationId 不能为空");
        Objects.requireNonNull(sessionId, "sessionId 不能为空");
        Objects.requireNonNull(executionId, "executionId 不能为空");
        source = source == null ? Source.AUTOMATION : source;
        Objects.requireNonNull(status, "status 不能为空");
        Objects.requireNonNull(owner, "owner 不能为空");
        Objects.requireNonNull(contract, "contract 不能为空");
        Objects.requireNonNull(budgetUsage, "budgetUsage 不能为空");
        Objects.requireNonNull(nextRunAt, "nextRunAt 不能为空");
        checkpoint = checkpoint == null ? Map.of() : Map.copyOf(checkpoint);
        Objects.requireNonNull(createdAt, "createdAt 不能为空");
        Objects.requireNonNull(updatedAt, "updatedAt 不能为空");
        if (attempts < 0 || consecutiveFailures < 0 || fencingToken < 0) {
            throw new IllegalArgumentException("次数和 fencingToken 不能为负数");
        }
        if (status == Status.RUNNING
                && (leaseOwner == null || leaseUntil == null || fencingToken < 1)) {
            throw new IllegalArgumentException("RUNNING 任务必须持有有效执行租约");
        }
        if (owner.kind() == OwnerKind.HUMAN && status == Status.RUNNING) {
            throw new IllegalArgumentException("人工接管期间 Agent 不能保持 RUNNING");
        }
    }

    public boolean terminal() {
        return status == Status.COMPLETED || status == Status.CANCELED || status == Status.FAILED;
    }

    public record BudgetUsage(
            long modelCalls, long modelTokens, long toolCalls, long toolUnits, BigDecimal credits) {
        public BudgetUsage {
            Objects.requireNonNull(credits, "credits 不能为空");
            if (modelCalls < 0
                    || modelTokens < 0
                    || toolCalls < 0
                    || toolUnits < 0
                    || credits.signum() < 0) {
                throw new IllegalArgumentException("预算用量不能为负数");
            }
        }

        public static BudgetUsage empty() {
            return new BudgetUsage(0, 0, 0, 0, BigDecimal.ZERO);
        }
    }

    public record Owner(OwnerKind kind, String ownerId) {
        public Owner {
            Objects.requireNonNull(kind, "owner kind 不能为空");
            if (ownerId == null || ownerId.isBlank()) {
                throw new IllegalArgumentException("ownerId 不能为空白");
            }
        }
    }

    public enum OwnerKind {
        ASSISTANT,
        AGENT,
        HUMAN
    }

    public enum Source {
        CONVERSATION,
        MANUAL,
        AUTOMATION
    }

    public enum Status {
        PENDING,
        RUNNING,
        PAUSED,
        AWAITING_AUTHORIZATION,
        AWAITING_CLARIFICATION,
        COMPLETED,
        CANCELED,
        FAILED
    }
}
