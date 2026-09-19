package com.xuejiai.aaf.framework.intelligent.assistant.port;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.assistant.application.AssistantCommand;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ArtifactCompletionContract.Evaluation;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ArtifactCompletionContract.InvocationReceipt;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Execution;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Task;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskDispatch;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskPlan;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/** TaskNode 首次物化、领取与结果提交的唯一短事务边界。 */
public interface TaskMaterializationPort {

    List<MaterializedDispatch> createPlannedTask(
            Task task, TaskPlan plan, AssistantCommand rootCommand, Instant at);

    List<MaterializedDispatch> promoteDirect(
            Execution execution,
            Task task,
            TaskPlan plan,
            AssistantCommand rootCommand,
            Instant at);

    List<MaterializedDispatch> materializeReadyNodes(
            TenantId tenantId, TaskId taskId, AssistantCommand rootCommand, Instant at);

    /** 停止 current plan 执行权并创建仅含 coordinator 的下一 revision，由 coordinator 重新规划。 */
    List<MaterializedDispatch> startTaskReplan(
            TenantId tenantId,
            UserId userId,
            TaskId taskId,
            String inputId,
            String reason,
            Instant at);

    /** 只停止目标 TaskNode，取消其 active ExecutorPlan，并为该节点创建 fresh attempt。 */
    List<MaterializedDispatch> restartNodeForPlanAmendment(
            TenantId tenantId,
            UserId userId,
            TaskId taskId,
            String nodeId,
            String inputId,
            String reason,
            Instant at);

    /** 提交可观察的暂停请求、立即关闭旧 Dispatch 执行权，并返回逐 Execution ACK 目标。 */
    PauseRequest requestPause(
            TenantId tenantId,
            UserId userId,
            TaskId taskId,
            String reason,
            Duration ackTimeout,
            Instant at);

    /** 先走 durable pause；只有稳定 PAUSED 后才把责任主体切换为当前用户。 */
    PauseRequest requestTakeOver(
            TenantId tenantId,
            UserId userId,
            TaskId taskId,
            String reason,
            Duration ackTimeout,
            Instant at);

    /** 幂等记录单个目标 Execution 的 AgentState 保存结果；最后一个 ACK 自动收敛 Task。 */
    Task acknowledgePause(PauseAckCommand command);

    /** 全局有界扫描仍在等待 ACK 的 PAUSING Task；候选不持有数据库锁。 */
    List<PausingTask> findPausing(int limit);

    /** ACK deadline 到期时把缺失 ACK 视为失败并收敛为 PAUSED + fresh attempt。 */
    boolean finalizeTimedOutPause(TenantId tenantId, TaskId taskId, Instant at);

    /** 根据已冻结的 pause resume mode，将 PAUSED 节点或 TASK_ROOT 恢复为可调度状态。 */
    ResumeResult resumePaused(TenantId tenantId, UserId userId, TaskId taskId, Instant at);

    /** 从当前 Human owner 交回合同 Owner Assistant，并强制创建 fresh attempt。 */
    ResumeResult handBack(TenantId tenantId, UserId userId, TaskId taskId, Instant at);

    /** 提交可观察的取消请求并立即关闭旧 Dispatch 执行权；不等待 runtime ACK。 */
    Task requestCancellation(
            TenantId tenantId, UserId userId, TaskId taskId, String reason, Instant at);

    /** 全局有界扫描待终结 Task；候选不持有数据库锁。 */
    List<CancelingTask> findCanceling(int limit);

    /** 在独立短事务中将单个 CANCELING Task 幂等终结为 CANCELED。 */
    boolean finalizeCancellation(TenantId tenantId, TaskId taskId, Instant at);

    Optional<ClaimedNode> claim(
            TenantId tenantId,
            String dispatchId,
            String workerId,
            Duration leaseTtl,
            ConversationLeasePort.Lease conversationLease,
            Instant at);

    CommitResult commit(NodeResultCommand command);

    /** 专业 Tool durable accepted 后冻结 receipt，并原子关闭当前 Dispatch 执行权。 */
    AwaitExternalResult awaitExternal(AwaitExternalCommand command);

    /** 有界扫描全部 AWAITING_EXTERNAL Execution；AIGC 信号与 scheduler 共用。 */
    List<AwaitingExternal> findAwaitingExternal(int limit);

    /** 按权威 AIGC Run ID 定向查询等待合同。 */
    List<AwaitingExternal> findAwaitingExternalByRunId(Long executionRunId);

    /** 根据权威 evaluator 结论幂等恢复；成功时为同一 Execution 创建 fresh Dispatch。 */
    ExternalRecoveryResult recoverExternal(
            InvocationReceipt receipt, Evaluation evaluation, Instant at);

    int recoverExpired(Instant at);

    List<MaterializedDispatch> findDue(Instant at, int limit);

    record MaterializedDispatch(TenantId tenantId, TaskId taskId, String dispatchId) {}

    record AwaitExternalCommand(
            InvocationReceipt receipt,
            String dispatchId,
            long dispatchGeneration,
            String dispatchLeaseOwner,
            Instant at) {
        public AwaitExternalCommand {
            java.util.Objects.requireNonNull(receipt, "receipt 不能为空");
            if (dispatchId == null || dispatchId.isBlank() || dispatchGeneration < 1) {
                throw new IllegalArgumentException("dispatch identity 不完整");
            }
            if (dispatchLeaseOwner == null || dispatchLeaseOwner.isBlank()) {
                throw new IllegalArgumentException("dispatchLeaseOwner 不能为空白");
            }
            java.util.Objects.requireNonNull(at, "at 不能为空");
        }

        public static AwaitExternalCommand from(
                InvocationReceipt receipt, InvocationContext context, Instant at) {
            java.util.Objects.requireNonNull(context, "context 不能为空");
            if (!receipt.tenantId().equals(context.tenantId())
                    || !receipt.taskId().equals(context.taskId())
                    || !receipt.executionId().equals(context.executionId())
                    || !java.util.Objects.equals(receipt.projectId(), context.projectId())
                    || receipt.dispatchFencingToken() != context.dispatchFencingToken()) {
                throw new IllegalArgumentException("receipt 与 InvocationContext identity 不一致");
            }
            return new AwaitExternalCommand(
                    receipt,
                    context.dispatchId(),
                    context.dispatchGeneration(),
                    context.dispatchLeaseOwner(),
                    at);
        }
    }

    record AwaitExternalResult(Task task, boolean created) {
        public AwaitExternalResult {
            java.util.Objects.requireNonNull(task, "task 不能为空");
        }
    }

    record AwaitingExternal(InvocationReceipt receipt) {
        public AwaitingExternal {
            java.util.Objects.requireNonNull(receipt, "receipt 不能为空");
        }
    }

    record ExternalRecoveryResult(
            boolean transitioned, List<MaterializedDispatch> releasedDispatches) {
        public ExternalRecoveryResult {
            releasedDispatches =
                    List.copyOf(
                            java.util.Objects.requireNonNull(
                                    releasedDispatches, "releasedDispatches 不能为空"));
        }
    }

    record ResumeResult(
            Task task, List<MaterializedDispatch> nodeDispatches, boolean rootDispatchPending) {
        public ResumeResult {
            java.util.Objects.requireNonNull(task, "task 不能为空");
            nodeDispatches =
                    List.copyOf(
                            java.util.Objects.requireNonNull(
                                    nodeDispatches, "nodeDispatches 不能为空"));
            if (rootDispatchPending && task.currentRootExecutionId() == null) {
                throw new IllegalArgumentException("rootDispatchPending 必须绑定 current TASK_ROOT");
            }
        }
    }

    record PauseRequest(
            Task task,
            String requestId,
            Instant deadlineAt,
            List<ExecutionId> targets,
            boolean created) {
        public PauseRequest {
            java.util.Objects.requireNonNull(task, "task 不能为空");
            if (requestId == null || requestId.isBlank()) {
                throw new IllegalArgumentException("requestId 不能为空白");
            }
            java.util.Objects.requireNonNull(deadlineAt, "deadlineAt 不能为空");
            targets = List.copyOf(java.util.Objects.requireNonNull(targets, "targets 不能为空"));
        }
    }

    record PauseAckCommand(
            TenantId tenantId,
            TaskId taskId,
            String requestId,
            ExecutionId executionId,
            boolean stateSaved,
            String stateSlotId,
            String stateSchema,
            Instant stateSavedAt,
            String failure,
            Instant at) {
        public PauseAckCommand {
            java.util.Objects.requireNonNull(tenantId, "tenantId 不能为空");
            java.util.Objects.requireNonNull(taskId, "taskId 不能为空");
            if (requestId == null || requestId.isBlank()) {
                throw new IllegalArgumentException("requestId 不能为空白");
            }
            java.util.Objects.requireNonNull(executionId, "executionId 不能为空");
            java.util.Objects.requireNonNull(at, "at 不能为空");
            if (stateSaved
                    && (stateSlotId == null
                            || stateSlotId.isBlank()
                            || stateSchema == null
                            || stateSchema.isBlank()
                            || stateSavedAt == null)) {
                throw new IllegalArgumentException("成功 ACK 必须携带 state slot/schema/savedAt");
            }
        }
    }

    record PausingTask(TenantId tenantId, TaskId taskId, Instant deadlineAt) {
        public PausingTask {
            java.util.Objects.requireNonNull(tenantId, "tenantId 不能为空");
            java.util.Objects.requireNonNull(taskId, "taskId 不能为空");
            java.util.Objects.requireNonNull(deadlineAt, "deadlineAt 不能为空");
        }
    }

    record CancelingTask(TenantId tenantId, TaskId taskId) {
        public CancelingTask {
            java.util.Objects.requireNonNull(tenantId, "tenantId 不能为空");
            java.util.Objects.requireNonNull(taskId, "taskId 不能为空");
        }
    }

    record ClaimedNode(
            Task task,
            TaskPlan plan,
            TaskPlan.TaskNode node,
            Execution execution,
            TaskDispatch dispatch,
            AssistantCommand command,
            long taskVersion,
            long planVersion,
            long nodeVersion,
            long executionVersion) {}

    record NodeResultCommand(
            TenantId tenantId,
            TaskId taskId,
            String planId,
            int planRevision,
            String nodeId,
            String executionId,
            int attemptNo,
            String dispatchId,
            long generation,
            long fencingToken,
            String leaseOwner,
            long expectedTaskVersion,
            long expectedPlanVersion,
            long expectedNodeVersion,
            long expectedExecutionVersion,
            String result,
            String failure,
            Instant at) {}

    record CommitResult(
            boolean accepted,
            Task task,
            TaskPlan.TaskNode node,
            List<MaterializedDispatch> releasedDispatches,
            String rejectionReason) {}
}
