package com.xuejiai.aaf.framework.intelligent.shared.event;

import java.time.Instant;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AgentId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AssistantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.CausationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.CorrelationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.EventId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.IdempotencyKey;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.RunId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/** AAF 智能层唯一的稳定执行事件契约。 */
public record ExecutionEvent(
        EventId eventId,
        TenantId tenantId,
        ConversationId conversationId,
        SessionId sessionId,
        TaskId taskId,
        ExecutionId executionId,
        RunId runId,
        ExecutionId parentExecutionId,
        long sequence,
        ExecutionEventType type,
        ExecutionEventStatus status,
        ControlMode controlMode,
        OwnerType ownerType,
        AssistantId assistantId,
        AgentId agentId,
        UserId userId,
        CorrelationId correlationId,
        CausationId causationId,
        IdempotencyKey idempotencyKey,
        ExecutionEventPayload payload,
        Instant createdAt,
        NodeIdentity nodeIdentity) {

    /**
     * 无编排节点身份的事件：DIRECT 直答、Assistant 自身的任务级事件本就不属于任何板上节点。
     *
     * <p>{@code nodeIdentity == null} 是有意义的取值。它是 {@code event_payload} JSONB 里的一个键，存量事件行缺该键即 反序列化为
     * null，因此本次新增不需要 DDL 变更。
     */
    public ExecutionEvent(
            EventId eventId,
            TenantId tenantId,
            ConversationId conversationId,
            SessionId sessionId,
            TaskId taskId,
            ExecutionId executionId,
            RunId runId,
            ExecutionId parentExecutionId,
            long sequence,
            ExecutionEventType type,
            ExecutionEventStatus status,
            ControlMode controlMode,
            OwnerType ownerType,
            AssistantId assistantId,
            AgentId agentId,
            UserId userId,
            CorrelationId correlationId,
            CausationId causationId,
            IdempotencyKey idempotencyKey,
            ExecutionEventPayload payload,
            Instant createdAt) {
        this(
                eventId,
                tenantId,
                conversationId,
                sessionId,
                taskId,
                executionId,
                runId,
                parentExecutionId,
                sequence,
                type,
                status,
                controlMode,
                ownerType,
                assistantId,
                agentId,
                userId,
                correlationId,
                causationId,
                idempotencyKey,
                payload,
                createdAt,
                null);
    }

    public ExecutionEvent {
        Objects.requireNonNull(eventId, "eventId 不能为空");
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(conversationId, "conversationId 不能为空");
        Objects.requireNonNull(sessionId, "sessionId 不能为空");
        Objects.requireNonNull(executionId, "executionId 不能为空");
        Objects.requireNonNull(runId, "runId 不能为空");
        Objects.requireNonNull(type, "type 不能为空");
        Objects.requireNonNull(status, "status 不能为空");
        Objects.requireNonNull(controlMode, "controlMode 不能为空");
        if (taskId == null && (controlMode != ControlMode.READ_ONLY || nodeIdentity != null)) {
            throw new IllegalArgumentException("无 Task 事件仅允许 READ_ONLY DIRECT");
        }
        Objects.requireNonNull(ownerType, "ownerType 不能为空");
        Objects.requireNonNull(correlationId, "correlationId 不能为空");
        Objects.requireNonNull(createdAt, "createdAt 不能为空");
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence 必须从 1 开始");
        }
        payload = payload == null ? ExecutionEventPayload.empty() : payload;
        validateOwner(ownerType, assistantId, agentId, userId);
    }

    /** 是否为没有直接原因事件的根事件。 */
    public boolean isRootEvent() {
        return causationId == null;
    }

    /** 当前事件是否记录了 execution 的终态。 */
    public boolean isTerminal() {
        return status.isTerminal();
    }

    /** 当前事件是否携带副作用或重放所需的幂等键。 */
    public boolean hasIdempotencyKey() {
        return idempotencyKey != null;
    }

    private static void validateOwner(
            OwnerType ownerType, AssistantId assistantId, AgentId agentId, UserId userId) {
        switch (ownerType) {
            case HUMAN -> Objects.requireNonNull(userId, "HUMAN owner 必须携带 userId");
            case ASSISTANT ->
                    Objects.requireNonNull(assistantId, "ASSISTANT owner 必须携带 assistantId");
            case AGENT -> Objects.requireNonNull(agentId, "AGENT owner 必须携带 agentId");
            case SYSTEM -> {
                // 系统责任主体不要求额外标识。
            }
        }
    }

    /** 用户授予的自主权和副作用上限。 */
    public enum ControlMode {
        READ_ONLY,
        COLLABORATIVE,
        DELEGATED,
        AUTOMATED
    }

    /** 当前执行责任主体类型。 */
    public enum OwnerType {
        SYSTEM,
        HUMAN,
        ASSISTANT,
        AGENT
    }

    /** 事件发生后 execution 的生命周期快照。 */
    public enum ExecutionEventStatus {
        DRAFT,
        PLANNING,
        AWAITING_AUTHORIZATION,
        AWAITING_CLARIFICATION,
        RUNNING,
        VERIFYING,
        PAUSED,
        COMPLETED,
        CANCELED,
        FAILED,
        REJECTED,
        RECOVERING;

        /** 是否为不可继续追加业务执行事件的终态。 */
        public boolean isTerminal() {
            return this == COMPLETED || this == CANCELED || this == FAILED || this == REJECTED;
        }
    }
}
