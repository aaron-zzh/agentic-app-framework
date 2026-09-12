package com.xuejiai.aaf.framework.intelligent.assistant.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.assistant.application.AssistantCommand;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Execution;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionInput;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Task;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskCheckpoint;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskDispatch;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskSnapshot;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/** Task、Execution、TaskDispatch 的单事务命令边界；三个实体不允许由应用层分别写入。 */
public interface TaskUnitOfWork {
    StoredTask create(TaskSnapshot snapshot, AssistantCommand command);

    TaskSnapshot save(TaskSnapshot snapshot, AssistantCommand command);

    Task saveRoot(Task task, ConversationLeasePort.Lease lease);

    BufferedInput bufferInput(ExecutionInput input);

    Optional<StoredTask> find(TenantId tenantId, TaskId taskId);

    Optional<Task> findTask(TenantId tenantId, TaskId taskId);

    List<Task> findTasksByConversation(
            TenantId tenantId, UserId userId, ConversationId conversationId);

    Execution createExecution(Execution execution, AssistantCommand command);

    Execution startNodeExecution(
            InvocationContext parentContext,
            Execution execution,
            AssistantCommand command,
            Instant at);

    Execution updateNodeExecutionStatus(
            InvocationContext parentContext,
            ExecutionId executionId,
            Execution.Status status,
            Instant at);

    Execution updateExecution(
            TenantId tenantId, ExecutionId executionId, Execution.Status status, Instant at);

    Optional<Execution> findExecution(TenantId tenantId, ExecutionId executionId);

    Optional<AssistantCommand> findCommand(TenantId tenantId, ExecutionId executionId);

    Execution recordSideEffectIntent(TenantId tenantId, ExecutionId executionId, Instant at);

    Optional<Execution> findLatestNodeExecution(TenantId tenantId, TaskId taskId, String nodeId);

    List<TaskSnapshot> list(TenantId tenantId, UserId userId);

    List<StoredTask> findPendingByConversation(TenantId tenantId, ConversationId conversationId);

    List<StoredTask> findDispatchable(Instant now, int limit);

    Optional<StoredTask> claim(
            TenantId tenantId, TaskId taskId, ConversationLeasePort.Lease lease, Instant now);

    TaskSnapshot renew(TaskId taskId, ConversationLeasePort.Lease lease, Instant now);

    void requireAgentExecution(InvocationContext context);

    <T> T accessAgentState(
            InvocationContext context, AgentStateAccess access, Supplier<T> operation);

    void requireExecution(
            TenantId tenantId,
            TaskId taskId,
            ExecutionId executionId,
            ConversationLeasePort.Lease lease);

    Task reserveModelCall(InvocationContext context, Instant at);

    Task recordModelUsage(InvocationContext context, long tokens, BigDecimal credits, Instant at);

    Task reserveToolCall(InvocationContext context, String action, Instant at);

    Task recordToolUsage(InvocationContext context, long units, BigDecimal credits, Instant at);

    TaskSnapshot checkpoint(
            InvocationContext context, TaskCheckpoint checkpoint, Instant nextRunAt, Instant at);

    TaskSnapshot complete(InvocationContext context, Map<String, Object> result, Instant at);

    TaskSnapshot fail(InvocationContext context, String failure, Instant at);

    int recoverExpired(Instant now);

    enum AgentStateAccess {
        ACTIVE,
        SUSPENDED_SNAPSHOT,
        STALE_SAFE_CLEANUP
    }

    record StoredTask(TaskSnapshot snapshot, AssistantCommand command) {
        public StoredTask {
            java.util.Objects.requireNonNull(snapshot, "snapshot 不能为空");
            java.util.Objects.requireNonNull(command, "command 不能为空");
        }

        public TaskSnapshot task() {
            return snapshot;
        }

        public Execution execution() {
            return snapshot.execution();
        }

        public TaskDispatch dispatch() {
            return snapshot.dispatch();
        }
    }

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
