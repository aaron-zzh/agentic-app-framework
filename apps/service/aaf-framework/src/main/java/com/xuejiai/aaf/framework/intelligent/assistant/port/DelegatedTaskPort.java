package com.xuejiai.aaf.framework.intelligent.assistant.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.assistant.application.AssistantCommand;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionInput;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskCheckpoint;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/** 委托任务、调度和预算的 PostgreSQL 唯一事实端口。 */
public interface DelegatedTaskPort {

    BufferedInput bufferInput(ExecutionInput input);

    Optional<StoredTask> find(TenantId tenantId, TaskId taskId);

    List<DelegatedTask> list(TenantId tenantId, UserId userId);

    List<StoredTask> findPendingByConversation(TenantId tenantId, ConversationId conversationId);

    List<StoredTask> findDispatchable(Instant now, int limit);

    Optional<StoredTask> claim(
            TenantId tenantId, TaskId taskId, ConversationLeasePort.Lease lease, Instant now);

    DelegatedTask renew(TaskId taskId, ConversationLeasePort.Lease lease, Instant now);

    void requireAgentExecution(InvocationContext context);

    void requireExecution(
            TenantId tenantId,
            TaskId taskId,
            ExecutionId executionId,
            ConversationLeasePort.Lease lease);

    DelegatedTask reserveModelCall(InvocationContext context, Instant at);

    DelegatedTask recordModelUsage(
            InvocationContext context, long tokens, BigDecimal credits, Instant at);

    DelegatedTask reserveToolCall(InvocationContext context, String action, Instant at);

    DelegatedTask recordToolUsage(
            InvocationContext context, long units, BigDecimal credits, Instant at);

    DelegatedTask checkpoint(
            InvocationContext context, TaskCheckpoint checkpoint, Instant nextRunAt, Instant at);

    DelegatedTask pause(
            TenantId tenantId,
            TaskId taskId,
            ConversationLeasePort.Lease lease,
            String reason,
            Instant at);

    DelegatedTask complete(InvocationContext context, Map<String, Object> result, Instant at);

    DelegatedTask fail(InvocationContext context, String failure, Instant at);

    DelegatedTask cancel(
            TenantId tenantId,
            UserId userId,
            TaskId taskId,
            String reason,
            ConversationLeasePort.Lease lease,
            Instant at);

    DelegatedTask takeOver(
            TenantId tenantId,
            UserId userId,
            TaskId taskId,
            String reason,
            ConversationLeasePort.Lease lease,
            Instant at);

    StoredTask handBack(
            TenantId tenantId,
            UserId userId,
            TaskId taskId,
            ExecutionId executionId,
            SessionId sessionId,
            ConversationLeasePort.Lease lease,
            Instant at);

    int recoverExpired(Instant now);

    record StoredTask(DelegatedTask task, AssistantCommand command) {}

    record BufferedInput(ExecutionInput input, boolean created) {
        public BufferedInput {
            java.util.Objects.requireNonNull(input, "input 不能为空");
        }
    }

    final class BudgetExceededException extends IllegalStateException {
        public BudgetExceededException(String message) {
            super(message);
        }
    }

    final class StaleExecutionException extends IllegalStateException {
        public StaleExecutionException(String message) {
            super(message);
        }
    }
}
