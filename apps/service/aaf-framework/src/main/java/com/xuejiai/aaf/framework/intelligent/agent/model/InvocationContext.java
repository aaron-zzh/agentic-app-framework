package com.xuejiai.aaf.framework.intelligent.agent.model;

import java.util.Objects;

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
    }
}
