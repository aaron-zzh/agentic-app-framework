package com.xuejiai.aaf.framework.intelligent.agent.model;

import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionContract;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort.Lease;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
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

/** 每次 Agent 调用显式携带的身份、状态槽位与轨迹上下文。 */
public record InvocationContext(
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

    public InvocationContext {
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(userId, "userId 不能为空");
        Objects.requireNonNull(conversationId, "conversationId 不能为空");
        Objects.requireNonNull(sessionId, "sessionId 不能为空");
        Objects.requireNonNull(taskId, "taskId 不能为空");
        Objects.requireNonNull(executionId, "executionId 不能为空");
        Objects.requireNonNull(runId, "runId 不能为空");
        Objects.requireNonNull(correlationId, "correlationId 不能为空");
        Objects.requireNonNull(controlMode, "controlMode 不能为空");
        Objects.requireNonNull(toolAuthorization, "toolAuthorization 不能为空");
        if (controlMode == ControlMode.DELEGATED) {
            Objects.requireNonNull(executionContract, "DELEGATED 调用必须携带 ExecutionContract");
            Objects.requireNonNull(lease, "DELEGATED 调用必须携带 conversation lease");
            executionContract.requireUsableAt(java.time.Instant.now());
            if (!tenantId.equals(lease.tenantId())
                    || !conversationId.equals(lease.conversationId())) {
                throw new IllegalArgumentException("InvocationContext 与 conversation lease 不一致");
            }
        }
    }
}
