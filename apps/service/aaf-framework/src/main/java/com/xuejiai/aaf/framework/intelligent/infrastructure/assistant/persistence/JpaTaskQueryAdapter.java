package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskDetails;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskDetails.DispatchDetails;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskDetails.ExecutionDetails;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskDetails.ExecutorPlanDetails;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskDetails.ExecutorPlanStepDetails;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskDetails.NodeDetails;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskDetails.PlanDetails;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskDetails.RootResultDetails;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskDispatch;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskPlan;
import com.xuejiai.aaf.framework.intelligent.assistant.model.plan.ExecutorPlan;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskPlanPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskQueryPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.plan.ExecutorPlanPort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/** 从 canonical facts 装配安全 API 投影；租约、fence、合同、checkpoint 与内部版本不出持久化边界。 */
public final class JpaTaskQueryAdapter implements TaskQueryPort {
    private final TaskRootRepository tasks;
    private final TaskExecutionRepository executions;
    private final TaskDispatchRepository dispatches;
    private final TaskPlanPort plans;
    private final ExecutorPlanPort executorPlans;

    public JpaTaskQueryAdapter(
            TaskRootRepository tasks,
            TaskExecutionRepository executions,
            TaskDispatchRepository dispatches,
            TaskPlanPort plans,
            ExecutorPlanPort executorPlans) {
        this.tasks = Objects.requireNonNull(tasks, "tasks 不能为空");
        this.executions = Objects.requireNonNull(executions, "executions 不能为空");
        this.dispatches = Objects.requireNonNull(dispatches, "dispatches 不能为空");
        this.plans = Objects.requireNonNull(plans, "plans 不能为空");
        this.executorPlans = Objects.requireNonNull(executorPlans, "executorPlans 不能为空");
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TaskDetails> find(TenantId tenantId, UserId userId, TaskId taskId) {
        return tasks.findByTenantIdAndTaskId(tenantId.value(), taskId.value())
                .filter(entity -> entity.getUserId().equals(userId.value()))
                .map(entity -> details(tenantId, entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskDetails> list(TenantId tenantId, UserId userId, ConversationId conversationId) {
        return tasks
                .findByTenantIdAndConversationIdAndUserIdOrderByIdDesc(
                        tenantId.value(), conversationId.value(), userId.value())
                .stream()
                .map(entity -> details(tenantId, entity))
                .toList();
    }

    private TaskDetails details(TenantId tenantId, TaskRootEntity entity) {
        var task = entity.getTask();
        var plan = plans.find(tenantId, task.taskId()).orElse(null);
        var attempts =
                executions
                        .findByTenantIdAndTaskIdOrderByAttemptNoAsc(
                                tenantId.value(), task.taskId().value())
                        .stream()
                        .map(
                                executionEntity -> {
                                    var execution = executionEntity.getExecution();
                                    var dispatch =
                                            dispatches
                                                    .findFirstByTenantIdAndExecutionIdOrderByIdDesc(
                                                            tenantId.value(),
                                                            execution.executionId().value())
                                                    .map(TaskDispatchEntity::getDispatch)
                                                    .map(JpaTaskQueryAdapter::dispatchDetails)
                                                    .orElse(null);
                                    return new ExecutionDetails(
                                            execution.executionId().value(),
                                            execution.nodeId(),
                                            execution.scope().name(),
                                            execution.attemptNo(),
                                            execution.status().name(),
                                            execution.ownerSnapshot().kind().name(),
                                            execution.consecutiveFailures(),
                                            dispatch,
                                            execution.createdAt(),
                                            execution.updatedAt());
                                })
                        .toList();
        return new TaskDetails(
                task.taskId().value(),
                task.conversationId().value(),
                task.originExecutionId() == null ? null : task.originExecutionId().value(),
                task.source().name(),
                task.priority(),
                task.status().name(),
                task.controlMode().name(),
                task.owner().kind().name(),
                plan == null ? null : plan.goal().description(),
                plan == null ? null : planDetails(tenantId, plan),
                task.rootResult() == null
                        ? null
                        : new RootResultDetails(
                                task.rootResult().result(),
                                task.rootResult().planRevision(),
                                task.rootResult().sourceNodeId(),
                                task.rootResult().committedAt()),
                attempts,
                task.createdAt(),
                task.updatedAt());
    }

    private PlanDetails planDetails(TenantId tenantId, TaskPlan plan) {
        var latestExecutorPlans = executorPlans.findLatestByTask(tenantId, plan.taskId());
        var nodes =
                plan.nodes().values().stream()
                        .sorted(Comparator.comparing(TaskPlan.TaskNode::nodeId))
                        .map(
                                node ->
                                        new NodeDetails(
                                                node.nodeId(),
                                                node.kind().name(),
                                                node.description(),
                                                node.dependsOn().stream().sorted().toList(),
                                                node.status().name(),
                                                node.attempts(),
                                                node.maxAttempts(),
                                                node.result(),
                                                node.failure(),
                                                executorPlanDetails(
                                                        tenantId,
                                                        latestExecutorPlans.get(node.nodeId()))))
                        .toList();
        return new PlanDetails(
                plan.planId(),
                plan.revision(),
                plan.planStatus().name(),
                plan.maxParallelism(),
                plan.failurePolicy().name(),
                plan.goal().aggregationContract().kind().name(),
                nodes);
    }

    private ExecutorPlanDetails executorPlanDetails(TenantId tenantId, ExecutorPlan plan) {
        if (plan == null) {
            return null;
        }
        var steps =
                executorPlans.findSteps(tenantId, plan.planId()).stream()
                        .map(
                                step ->
                                        new ExecutorPlanStepDetails(
                                                step.ordinal(),
                                                step.title(),
                                                step.status().name(),
                                                step.resultRef(),
                                                step.failureCode()))
                        .toList();
        return new ExecutorPlanDetails(plan.revision(), plan.status().name(), plan.goal(), steps);
    }

    private static DispatchDetails dispatchDetails(TaskDispatch dispatch) {
        return new DispatchDetails(
                dispatch.status().name(),
                dispatch.nextRunAt(),
                dispatch.deliveryAttempts(),
                dispatch.updatedAt());
    }
}
