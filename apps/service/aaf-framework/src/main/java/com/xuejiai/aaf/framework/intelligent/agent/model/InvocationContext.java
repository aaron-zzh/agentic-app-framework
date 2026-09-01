package com.xuejiai.aaf.framework.intelligent.agent.model;

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

/** 每次 Agent 调用显式携带的身份、状态槽位与轨迹上下文。 */
// nodeIdentity 由编排层填充；DIRECT 直答无编排板时为 null，见 NodeIdentity 类注释
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
        ToolAuthorizationContext toolAuthorization,
        NodeIdentity nodeIdentity) {

    /**
     * 无编排节点身份的调用：DIRECT 直答、Assistant 自身发起的调用等场景本就不属于任何 TaskBoard 节点。
     *
     * <p>这不是兼容层——{@code nodeIdentity == null} 是一个有意义的取值，表示"本次调用不在编排板上"。编排层派发执行者时
     * 必须使用完整构造器显式给出节点身份，缺失会让 AG-UI 无法区分应答者与执行者。
     */
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
