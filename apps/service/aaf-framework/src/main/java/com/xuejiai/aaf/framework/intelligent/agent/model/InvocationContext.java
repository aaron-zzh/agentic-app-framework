package com.xuejiai.aaf.framework.intelligent.agent.model;

import java.time.Instant;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionContract;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort.Lease;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.event.NodeIdentity;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AssistantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.CausationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.CorrelationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.IdempotencyKey;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.RunId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/** 每次 Agent 调用显式携带的身份、状态槽位、dispatch fence 与轨迹上下文。 */
public record InvocationContext(
        TenantId tenantId,
        UserId userId,
        Long workspaceId,
        AssistantId assistantId,
        ConversationId conversationId,
        SessionId sessionId,
        TaskId taskId,
        ExecutionId executionId,
        String stateSlotId,
        RunId runId,
        ExecutionId parentExecutionId,
        CorrelationId correlationId,
        CausationId causationId,
        IdempotencyKey idempotencyKey,
        ControlMode controlMode,
        ExecutionContract executionContract,
        Lease lease,
        ToolAuthorizationContext toolAuthorization,
        NodeIdentity nodeIdentity,
        String dispatchId,
        long dispatchGeneration,
        long dispatchFencingToken,
        String dispatchLeaseOwner,
        Instant dispatchLeaseUntil) {

    public InvocationContext(
            TenantId tenantId,
            UserId userId,
            Long workspaceId,
            AssistantId assistantId,
            ConversationId conversationId,
            SessionId sessionId,
            TaskId taskId,
            ExecutionId executionId,
            RunId runId,
            ExecutionId parentExecutionId,
            CorrelationId correlationId,
            CausationId causationId,
            IdempotencyKey idempotencyKey,
            ControlMode controlMode,
            ExecutionContract executionContract,
            Lease lease,
            ToolAuthorizationContext toolAuthorization,
            NodeIdentity nodeIdentity) {
        this(
                tenantId,
                userId,
                workspaceId,
                assistantId,
                conversationId,
                sessionId,
                taskId,
                executionId,
                executionId.value(),
                runId,
                parentExecutionId,
                correlationId,
                causationId,
                idempotencyKey,
                controlMode,
                executionContract,
                lease,
                toolAuthorization,
                nodeIdentity,
                null,
                0,
                0,
                null,
                null);
    }

    public InvocationContext(
            TenantId tenantId,
            UserId userId,
            Long workspaceId,
            AssistantId assistantId,
            ConversationId conversationId,
            SessionId sessionId,
            TaskId taskId,
            ExecutionId executionId,
            RunId runId,
            ExecutionId parentExecutionId,
            CorrelationId correlationId,
            CausationId causationId,
            IdempotencyKey idempotencyKey,
            ControlMode controlMode,
            ExecutionContract executionContract,
            Lease lease,
            ToolAuthorizationContext toolAuthorization) {
        this(
                tenantId,
                userId,
                workspaceId,
                assistantId,
                conversationId,
                sessionId,
                taskId,
                executionId,
                runId,
                parentExecutionId,
                correlationId,
                causationId,
                idempotencyKey,
                controlMode,
                executionContract,
                lease,
                toolAuthorization,
                null);
    }

    public InvocationContext {
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(userId, "userId 不能为空");
        Objects.requireNonNull(conversationId, "conversationId 不能为空");
        Objects.requireNonNull(sessionId, "sessionId 不能为空");
        Objects.requireNonNull(executionId, "executionId 不能为空");
        if (stateSlotId == null || stateSlotId.isBlank()) {
            throw new IllegalArgumentException("stateSlotId 不能为空白");
        }
        Objects.requireNonNull(runId, "runId 不能为空");
        Objects.requireNonNull(correlationId, "correlationId 不能为空");
        Objects.requireNonNull(controlMode, "controlMode 不能为空");
        Objects.requireNonNull(toolAuthorization, "toolAuthorization 不能为空");
        if (taskId == null
                && (controlMode != ControlMode.READ_ONLY
                        || lease != null
                        || nodeIdentity != null
                        || dispatchId != null)) {
            throw new IllegalArgumentException("无 Task Agent 调用仅允许 READ_ONLY DIRECT");
        }
        if (controlMode == ControlMode.DELEGATED) {
            Objects.requireNonNull(executionContract, "DELEGATED 调用必须携带 ExecutionContract");
            executionContract.requireUsableAt(Instant.now());
        }
        if (lease != null
                && (!tenantId.equals(lease.tenantId())
                        || !conversationId.equals(lease.conversationId()))) {
            throw new IllegalArgumentException("InvocationContext 与 conversation lease 不一致");
        }
        var hasDispatch = dispatchId != null;
        if (hasDispatch
                != (dispatchGeneration > 0
                        && dispatchFencingToken > 0
                        && dispatchLeaseOwner != null
                        && dispatchLeaseUntil != null)) {
            throw new IllegalArgumentException("dispatch identity 必须完整存在或完整缺省");
        }
        if (hasDispatch && (dispatchId.isBlank() || dispatchLeaseOwner.isBlank())) {
            throw new IllegalArgumentException("dispatch identity 不能为空白");
        }
    }
}
