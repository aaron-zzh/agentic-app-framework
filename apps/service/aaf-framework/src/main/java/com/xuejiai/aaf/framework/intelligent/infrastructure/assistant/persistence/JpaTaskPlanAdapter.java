package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskDagService;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskDependency;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskNodeDefinition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskPlan;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskPlanDraft;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort.Lease;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskPlanPort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/** TaskPlan/TaskNode/TaskDependency 的 canonical PostgreSQL 适配器。 */
public class JpaTaskPlanAdapter implements TaskPlanPort {
    private final TaskRootRepository tasks;
    private final TaskPlanRepository plans;
    private final TaskNodeRepository nodes;
    private final TaskDependencyRepository dependencies;
    private final ConversationLeasePort leases;
    private final TaskDagService dag = new TaskDagService();

    public JpaTaskPlanAdapter(
            TaskRootRepository tasks,
            TaskPlanRepository plans,
            TaskNodeRepository nodes,
            TaskDependencyRepository dependencies,
            ConversationLeasePort leases) {
        this.tasks = Objects.requireNonNull(tasks, "tasks 不能为空");
        this.plans = Objects.requireNonNull(plans, "plans 不能为空");
        this.nodes = Objects.requireNonNull(nodes, "nodes 不能为空");
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies 不能为空");
        this.leases = Objects.requireNonNull(leases, "leases 不能为空");
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TaskPlan> find(TenantId tenantId, TaskId taskId) {
        var task = tasks.findByTenantIdAndTaskId(tenantId.value(), taskId.value()).orElse(null);
        if (task == null || task.getCurrentPlanId() == null) {
            return Optional.empty();
        }
        return plans.findByTenantIdAndTaskIdAndPlanIdAndPlanRevision(
                        tenantId.value(),
                        taskId.value(),
                        task.getCurrentPlanId(),
                        task.getCurrentPlanRevision())
                .map(entity -> assemble(tenantId, entity));
    }

    @Override
    @Transactional
    public TaskPlan update(TenantId tenantId, TaskPlan plan, Lease lease) {
        Objects.requireNonNull(plan, "plan 不能为空");
        var entity = requireLocked(tenantId, plan.taskId(), lease);
        var current = assembleLocked(tenantId, entity);
        if (!current.planId().equals(plan.planId())
                || current.revision() != plan.revision()
                || current.planStatus() != TaskPlan.PlanStatus.FROZEN
                || plan.planStatus() != TaskPlan.PlanStatus.FROZEN
                || !current.graphHash().equals(plan.graphHash())
                || !current.nodes().keySet().equals(plan.nodes().keySet())) {
            throw new IllegalStateException("冻结 TaskPlan 更新不得改变 plan 或 graph 身份");
        }
        var changed = withMetadata(current, plan);
        persistNodes(tenantId, changed, false);
        return changed;
    }

    @Override
    @Transactional
    public TaskPlan applyTaskPlanDraft(
            TenantId tenantId, TaskId taskId, TaskPlanDraft draft, Lease lease) {
        var entity = requireLocked(tenantId, taskId, lease);
        var current = assembleLocked(tenantId, entity);
        var changed = current.applyTaskPlanDraft(draft);
        entity.setStatus(TaskPlan.PlanStatus.SUPERSEDED.name());
        plans.save(entity);
        var next =
                new TaskPlan(
                        current.planId(),
                        current.taskId(),
                        current.revision() + 1,
                        TaskPlan.PlanStatus.FROZEN,
                        changed.goal(),
                        changed.maxParallelism(),
                        current.failurePolicy(),
                        changed.graphHash(),
                        0,
                        changed.nodes());
        var stored = persist(tenantId, next, new TaskPlanEntity(), true);
        var task = tasks.findForUpdate(tenantId.value(), taskId.value()).orElseThrow();
        task.setTask(
                task.getTask()
                        .withCurrentPlan(next.planId(), next.revision(), java.time.Instant.now()));
        tasks.saveAndFlush(task);
        return stored;
    }

    @Override
    @Transactional
    public TaskPlan completeNode(
            TenantId tenantId, TaskId taskId, String nodeId, String result, Lease lease) {
        return mutate(tenantId, taskId, lease, plan -> plan.complete(nodeId, result));
    }

    @Override
    @Transactional
    public TaskPlan failNode(
            TenantId tenantId,
            TaskId taskId,
            String nodeId,
            String failure,
            boolean transientFailure,
            Lease lease) {
        return mutate(
                tenantId, taskId, lease, plan -> plan.fail(nodeId, failure, transientFailure));
    }

    @Override
    @Transactional
    public TaskPlan interruptNode(
            TenantId tenantId, TaskId taskId, String nodeId, boolean retryable, Lease lease) {
        return mutate(tenantId, taskId, lease, plan -> plan.interrupt(nodeId, retryable));
    }

    @Override
    @Transactional
    public TaskPlan interruptRunning(
            TenantId tenantId, TaskId taskId, boolean retryable, Lease lease) {
        return mutate(tenantId, taskId, lease, plan -> plan.interruptRunning(retryable));
    }

    private TaskPlan mutate(
            TenantId tenantId,
            TaskId taskId,
            Lease lease,
            java.util.function.UnaryOperator<TaskPlan> mutation) {
        var entity = requireLocked(tenantId, taskId, lease);
        var current = assembleLocked(tenantId, entity);
        var changed = withMetadata(current, mutation.apply(current));
        persistNodes(tenantId, changed, false);
        return changed;
    }

    private TaskPlanEntity requireLocked(TenantId tenantId, TaskId taskId, Lease lease) {
        leases.requireCurrent(lease);
        if (!tenantId.equals(lease.tenantId())) {
            throw new IllegalStateException("TaskPlan tenant 与 conversation lease 不一致");
        }
        var task =
                tasks.findForUpdate(tenantId.value(), taskId.value())
                        .orElseThrow(
                                () -> new IllegalArgumentException("Task 不存在: " + taskId.value()));
        if (task.getCurrentPlanId() == null) {
            throw new IllegalArgumentException("Task current plan 不存在: " + taskId.value());
        }
        return plans.findCurrentForUpdate(
                        tenantId.value(),
                        taskId.value(),
                        task.getCurrentPlanId(),
                        task.getCurrentPlanRevision())
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Task current plan 不存在: " + taskId.value()));
    }

    private TaskPlan assembleLocked(TenantId tenantId, TaskPlanEntity entity) {
        nodes.findPlanNodesForUpdate(
                tenantId.value(), entity.getTaskId(), entity.getPlanId(), entity.getPlanRevision());
        return assemble(tenantId, entity);
    }

    private TaskPlan assemble(TenantId tenantId, TaskPlanEntity entity) {
        var predecessorIds = new LinkedHashMap<String, LinkedHashSet<String>>();
        dependencies
                .findByTenantIdAndTaskIdAndPlanIdAndPlanRevisionOrderBySuccessorNodeIdAscPredecessorNodeIdAsc(
                        tenantId.value(),
                        entity.getTaskId(),
                        entity.getPlanId(),
                        entity.getPlanRevision())
                .forEach(
                        edge ->
                                predecessorIds
                                        .computeIfAbsent(
                                                edge.getSuccessorNodeId(),
                                                ignored -> new LinkedHashSet<>())
                                        .add(edge.getPredecessorNodeId()));
        var mapped = new LinkedHashMap<String, TaskPlan.TaskNode>();
        nodes.findByTenantIdAndTaskIdAndPlanIdAndPlanRevisionOrderByNodeId(
                        tenantId.value(),
                        entity.getTaskId(),
                        entity.getPlanId(),
                        entity.getPlanRevision())
                .forEach(
                        node ->
                                mapped.put(
                                        node.getNodeId(),
                                        node.toDomain(
                                                predecessorIds.getOrDefault(
                                                        node.getNodeId(), new LinkedHashSet<>()))));
        return new TaskPlan(
                entity.getPlanId(),
                new TaskId(entity.getTaskId()),
                entity.getPlanRevision(),
                TaskPlan.PlanStatus.valueOf(entity.getStatus()),
                entity.getGoal(),
                entity.getMaxParallelism(),
                TaskPlan.FailurePolicy.valueOf(entity.getFailurePolicy()),
                entity.getGraphHash(),
                entity.getRuntimeLockVersion(),
                mapped);
    }

    private TaskPlan persist(
            TenantId tenantId, TaskPlan plan, TaskPlanEntity entity, boolean replaceGraph) {
        entity.setTenantId(tenantId.value());
        entity.setTaskId(plan.taskId().value());
        entity.setPlanId(plan.planId());
        entity.setPlanRevision(plan.revision());
        entity.setStatus(plan.planStatus().name());
        entity.setMaxParallelism(plan.maxParallelism());
        entity.setFailurePolicy(plan.failurePolicy().name());
        entity.setGraphHash(plan.graphHash());
        entity.setGoal(plan.goal());
        plans.save(entity);
        persistNodes(tenantId, plan, replaceGraph);
        if (replaceGraph) {
            persistDependencies(tenantId, plan);
        }
        plans.flush();
        return plan;
    }

    private void persistNodes(TenantId tenantId, TaskPlan plan, boolean replaceGraph) {
        var existing =
                nodes.findByTenantIdAndTaskIdAndPlanIdAndPlanRevisionOrderByNodeId(
                        tenantId.value(), plan.taskId().value(), plan.planId(), plan.revision());
        var byId = new LinkedHashMap<String, TaskNodeEntity>();
        existing.forEach(entity -> byId.put(entity.getNodeId(), entity));
        if (!replaceGraph && !byId.keySet().equals(plan.nodes().keySet())) {
            throw new IllegalStateException("冻结 TaskPlan 禁止改变节点集合");
        }
        for (var node : plan.nodes().values()) {
            var entity = byId.getOrDefault(node.nodeId(), new TaskNodeEntity());
            entity.setTenantId(tenantId.value());
            entity.setTaskId(plan.taskId().value());
            entity.setPlanId(plan.planId());
            entity.setPlanRevision(plan.revision());
            entity.setNodeId(node.nodeId());
            entity.setStatus(node.status().name());
            entity.setCurrentExecutionId(
                    node.executionId() == null ? null : node.executionId().value());
            entity.setCurrentSessionId(node.sessionId() == null ? null : node.sessionId().value());
            entity.setCurrentAttempt(node.attempts());
            entity.setDefinition(TaskNodeDefinition.from(node));
            entity.setResult(node.result());
            entity.setFailure(node.failure());
            entity.setClarifiedParameters(node.clarifiedParameters());
            nodes.save(entity);
        }
        nodes.flush();
    }

    private void persistDependencies(TenantId tenantId, TaskPlan plan) {
        dependencies.deleteByTenantIdAndTaskIdAndPlanIdAndPlanRevision(
                tenantId.value(), plan.taskId().value(), plan.planId(), plan.revision());
        for (TaskDependency dependency : plan.dependencies()) {
            var entity = new TaskDependencyEntity();
            entity.setTenantId(tenantId.value());
            entity.setTaskId(plan.taskId().value());
            entity.setPlanId(plan.planId());
            entity.setPlanRevision(plan.revision());
            entity.setPredecessorNodeId(dependency.predecessorNodeId());
            entity.setSuccessorNodeId(dependency.successorNodeId());
            entity.setDependencyType(dependency.type().name());
            dependencies.save(entity);
        }
        dependencies.flush();
    }

    private static TaskPlan withMetadata(TaskPlan source, TaskPlan changed) {
        return new TaskPlan(
                source.planId(),
                source.taskId(),
                source.revision(),
                source.planStatus(),
                changed.goal(),
                changed.maxParallelism(),
                source.failurePolicy(),
                changed.graphHash(),
                source.version(),
                changed.nodes());
    }
}
