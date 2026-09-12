package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.intelligent.assistant.application.AssistantCommand;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Execution;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionInput;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionResumePolicy;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Task;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskDagService;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskDependency;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskDispatch;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskNodeDefinition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskPlan;
import com.xuejiai.aaf.framework.intelligent.assistant.model.plan.ExecutorPlan;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort.Lease;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskMaterializationPort;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.plan.ExecutorPlanRepository;
import com.xuejiai.aaf.framework.intelligent.infrastructure.trace.persistence.SynchronousExecutionEventWriter;
import com.xuejiai.aaf.framework.intelligent.shared.event.CanonicalExecutionEventId;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ExecutionEventStatus;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.OwnerType;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventPayload;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/** PostgreSQL canonical node materialization 与 dispatch fencing 实现。 */
public final class JpaTaskMaterializationAdapter implements TaskMaterializationPort {
    private static final String PAUSE_REQUEST_ID = "pauseRequestId";
    private static final String PAUSE_TARGETS = "pauseTargets";
    private static final String PAUSE_ACKS = "pauseAcks";
    private static final String PAUSE_DEADLINE_AT = "pauseAckDeadlineAt";
    private static final String PAUSE_RESUME_MODE = "pauseResumeMode";
    private static final String PAUSE_PURPOSE = "pausePurpose";
    private static final String PAUSE_PURPOSE_USER = "PAUSE";
    private static final String PAUSE_PURPOSE_TAKE_OVER = "TAKE_OVER";
    private static final String TAKE_OVER_USER_ID = "takeOverUserId";
    private static final String HAND_BACK_COMPLETED_AT = "handBackCompletedAt";
    private static final String HAND_BACK_REQUEST_ID = "handBackRequestId";
    private static final String AGENT_STATE_SCHEMA = "agentscope-agent-state-v1";

    private final TaskRootRepository tasks;
    private final TaskPlanRepository plans;
    private final TaskNodeRepository nodes;
    private final TaskDependencyRepository dependencies;
    private final TaskExecutionRepository executions;
    private final TaskDispatchRepository dispatches;
    private final TaskInputRepository inputs;
    private final ExecutorPlanRepository executorPlans;
    private final SynchronousExecutionEventWriter eventWriter;
    private final TaskTransitionOutboxRepository transitionOutbox;

    public JpaTaskMaterializationAdapter(
            TaskRootRepository tasks,
            TaskPlanRepository plans,
            TaskNodeRepository nodes,
            TaskDependencyRepository dependencies,
            TaskExecutionRepository executions,
            TaskDispatchRepository dispatches,
            TaskInputRepository inputs,
            ExecutorPlanRepository executorPlans,
            SynchronousExecutionEventWriter eventWriter,
            TaskTransitionOutboxRepository transitionOutbox) {
        this.tasks = Objects.requireNonNull(tasks, "tasks 不能为空");
        this.plans = Objects.requireNonNull(plans, "plans 不能为空");
        this.nodes = Objects.requireNonNull(nodes, "nodes 不能为空");
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies 不能为空");
        this.executions = Objects.requireNonNull(executions, "executions 不能为空");
        this.dispatches = Objects.requireNonNull(dispatches, "dispatches 不能为空");
        this.inputs = Objects.requireNonNull(inputs, "inputs 不能为空");
        this.executorPlans = Objects.requireNonNull(executorPlans, "executorPlans 不能为空");
        this.eventWriter = Objects.requireNonNull(eventWriter, "eventWriter 不能为空");
        this.transitionOutbox = Objects.requireNonNull(transitionOutbox, "transitionOutbox 不能为空");
    }

    @Override
    @Transactional
    public List<MaterializedDispatch> createPlannedTask(
            Task task, TaskPlan plan, AssistantCommand rootCommand, Instant at) {
        Objects.requireNonNull(task, "task 不能为空");
        Objects.requireNonNull(plan, "plan 不能为空");
        Objects.requireNonNull(rootCommand, "rootCommand 不能为空");
        Objects.requireNonNull(at, "at 不能为空");
        if (!task.tenantId().equals(rootCommand.tenantId())
                || !task.taskId().equals(rootCommand.taskId())
                || !task.taskId().equals(plan.taskId())
                || task.currentPlanId() != null
                || task.currentRootExecutionId() != null
                || plan.planStatus() != TaskPlan.PlanStatus.FROZEN) {
            throw new IllegalArgumentException("planned Task/Plan/command identity 不一致");
        }
        if (tasks.findByTenantIdAndTaskId(task.tenantId().value(), task.taskId().value())
                .isPresent()) {
            throw new IllegalStateException("Task 已存在: " + task.taskId().value());
        }
        var failures = new TaskDagService().validate(plan);
        if (!failures.isEmpty()) {
            throw new IllegalArgumentException("TaskPlan freeze rejected: " + failures);
        }
        var plannedTask = task.withCurrentPlan(plan.planId(), plan.revision(), at);
        var taskEntity = new TaskRootEntity();
        taskEntity.setTask(plannedTask);
        tasks.save(taskEntity);

        var planEntity = new TaskPlanEntity();
        planEntity.setTenantId(task.tenantId().value());
        planEntity.setTaskId(task.taskId().value());
        planEntity.setPlanId(plan.planId());
        planEntity.setPlanRevision(plan.revision());
        planEntity.setStatus(plan.planStatus().name());
        planEntity.setMaxParallelism(plan.maxParallelism());
        planEntity.setFailurePolicy(plan.failurePolicy().name());
        planEntity.setGraphHash(plan.graphHash());
        planEntity.setGoal(plan.goal());
        plans.save(planEntity);
        for (var node : plan.nodes().values()) {
            var nodeEntity = new TaskNodeEntity();
            nodeEntity.setTenantId(task.tenantId().value());
            nodeEntity.setTaskId(task.taskId().value());
            nodeEntity.setPlanId(plan.planId());
            nodeEntity.setPlanRevision(plan.revision());
            nodeEntity.setNodeId(node.nodeId());
            nodeEntity.setStatus(node.status().name());
            nodeEntity.setCurrentExecutionId(null);
            nodeEntity.setCurrentSessionId(null);
            nodeEntity.setCurrentAttempt(0);
            nodeEntity.setDefinition(TaskNodeDefinition.from(node));
            nodeEntity.setResult(null);
            nodeEntity.setFailure(null);
            nodeEntity.setClarifiedParameters(node.clarifiedParameters());
            nodes.save(nodeEntity);
        }
        for (TaskDependency dependency : plan.dependencies()) {
            var dependencyEntity = new TaskDependencyEntity();
            dependencyEntity.setTenantId(task.tenantId().value());
            dependencyEntity.setTaskId(task.taskId().value());
            dependencyEntity.setPlanId(plan.planId());
            dependencyEntity.setPlanRevision(plan.revision());
            dependencyEntity.setPredecessorNodeId(dependency.predecessorNodeId());
            dependencyEntity.setSuccessorNodeId(dependency.successorNodeId());
            dependencyEntity.setDependencyType(dependency.type().name());
            dependencies.save(dependencyEntity);
        }
        dependencies.flush();
        nodes.flush();
        plans.flush();
        tasks.flush();
        return materializeLocked(task.tenantId(), task.taskId(), rootCommand, at);
    }

    @Override
    @Transactional
    public List<MaterializedDispatch> promoteDirect(
            Execution requestedOrigin,
            Task task,
            TaskPlan plan,
            AssistantCommand rootCommand,
            Instant at) {
        Objects.requireNonNull(requestedOrigin, "origin Execution 不能为空");
        Objects.requireNonNull(task, "task 不能为空");
        Objects.requireNonNull(plan, "plan 不能为空");
        Objects.requireNonNull(rootCommand, "rootCommand 不能为空");
        Objects.requireNonNull(at, "at 不能为空");
        var originEntity =
                executions
                        .findForUpdate(
                                requestedOrigin.tenantId().value(),
                                requestedOrigin.executionId().value())
                        .orElseThrow(
                                () -> new IllegalArgumentException("origin DIRECT Execution 不存在"));
        var origin = originEntity.getExecution();
        var existing =
                tasks.findByTenantIdAndOriginExecutionId(
                                origin.tenantId().value(), origin.executionId().value())
                        .orElse(null);
        if (existing != null) {
            if (!existing.getTaskId().equals(task.taskId().value())
                    || origin.promotionState() != Execution.PromotionState.PROMOTED) {
                throw stale("originExecutionId 已绑定不同 promotion");
            }
            return List.of();
        }
        if (origin.scope() != Execution.Scope.DIRECT
                || !origin.tenantId().equals(task.tenantId())
                || !origin.userId().equals(task.userId())
                || !origin.conversationId().equals(task.conversationId())
                || !origin.executionId().equals(task.originExecutionId())
                || !origin.runId().equals(task.originRunId())
                || !origin.correlationId().equals(task.originCorrelationId())
                || task.source() != Task.Source.PROMOTION
                || task.originInputRef() == null
                || task.originInputRef().isBlank()
                || task.publicContextRef() == null
                || task.publicContextRef().isBlank()
                || !task.taskId().equals(plan.taskId())
                || !task.taskId().equals(rootCommand.taskId())
                || !origin.runId().equals(rootCommand.runId())
                || !origin.correlationId().equals(rootCommand.correlationId())) {
            throw new IllegalArgumentException("DIRECT promotion identity/lineage 不一致");
        }
        originEntity.setExecution(origin.beginPromotion(at));
        executions.saveAndFlush(originEntity);
        var materialized = createPlannedTask(task, plan, rootCommand, at);
        originEntity.setExecution(originEntity.getExecution().completePromotion(at));
        executions.saveAndFlush(originEntity);
        appendEventsAndOutbox(
                List.of(promotionEvent(task, originEntity.getExecution(), rootCommand, at)),
                0,
                task.taskId());
        return materialized;
    }

    @Override
    @Transactional
    public List<MaterializedDispatch> materializeReadyNodes(
            TenantId tenantId, TaskId taskId, AssistantCommand rootCommand, Instant at) {
        return materializeLocked(tenantId, taskId, rootCommand, at);
    }

    @Override
    @Transactional
    public List<MaterializedDispatch> startTaskReplan(
            TenantId tenantId,
            UserId userId,
            TaskId taskId,
            String inputId,
            String reason,
            Instant at) {
        var normalizedReason = requireAmendment(reason);
        var taskEntity = requireOwnedTask(tenantId, userId, taskId);
        var amendmentInput = requireAmendmentInput(tenantId, userId, taskId, inputId);
        if (amendmentInput.getConsumedAt() != null) {
            return List.of();
        }
        var task = taskEntity.getTask();
        if (task.terminal()
                || task.status() == Task.Status.PAUSING
                || task.status() == Task.Status.CANCELING
                || task.currentPlanId() == null) {
            throw new IllegalStateException("只有非终态 planned Task 可以重新规划");
        }
        var planEntity =
                plans.findCurrentForUpdate(
                                tenantId.value(),
                                taskId.value(),
                                task.currentPlanId(),
                                task.currentPlanRevision())
                        .orElseThrow(() -> stale("Task current plan 不存在"));
        var nodeEntities =
                nodes.findPlanNodesForUpdate(
                        tenantId.value(),
                        taskId.value(),
                        task.currentPlanId(),
                        task.currentPlanRevision());
        var current = assemblePlan(tenantId, planEntity, nodeEntities);
        var coordinator =
                current.nodes().values().stream()
                        .filter(node -> node.kind() == TaskPlan.TaskNode.Kind.COORDINATOR)
                        .findFirst()
                        .orElseThrow(
                                () -> new IllegalStateException("TaskPlan 缺少 coordinator，不能对话重规划"));
        var sourceCommand = sourceCommand(tenantId, nodeEntities, coordinator.nodeId());
        var anchor =
                executions
                        .findForUpdate(tenantId.value(), sourceCommand.executionId().value())
                        .orElseThrow(() -> stale("coordinator Execution 不存在"));
        var fencingToken = 0L;
        for (var node : nodeEntities) {
            fencingToken =
                    Math.max(
                            fencingToken, closeNodeExecution(tenantId, node, normalizedReason, at));
            cancelExecutorPlans(tenantId, taskId, node.getNodeId(), at);
            if (!TaskPlan.Status.COMPLETED.name().equals(node.getStatus())
                    && !TaskPlan.Status.FAILED.name().equals(node.getStatus())) {
                node.setStatus(TaskPlan.Status.CANCELED.name());
                node.setFailure(normalizedReason);
                nodes.save(node);
            }
        }
        planEntity.setStatus(TaskPlan.PlanStatus.SUPERSEDED.name());
        plans.saveAndFlush(planEntity);
        var next = replanningPlan(current, coordinator, normalizedReason);
        persistPlanRevision(tenantId, next);
        var replanning =
                task.withRuntime(
                                Task.Status.READY,
                                task.owner(),
                                task.budgetUsage(),
                                task.checkpoint().withAnnotation("planAmendment", normalizedReason),
                                task.recoveryPoint(),
                                at)
                        .withCurrentPlan(next.planId(), next.revision(), at);
        taskEntity.setTask(replanning);
        tasks.saveAndFlush(taskEntity);
        consumeInput(amendmentInput, at);
        appendEventsAndOutbox(
                List.of(
                        taskAmendedEvent(
                                replanning,
                                anchor.getExecution(),
                                userId,
                                "TASK_PLAN",
                                normalizedReason,
                                inputId,
                                at)),
                fencingToken,
                taskId);
        return materializeLocked(tenantId, taskId, sourceCommand, at);
    }

    @Override
    @Transactional
    public List<MaterializedDispatch> restartNodeForPlanAmendment(
            TenantId tenantId,
            UserId userId,
            TaskId taskId,
            String nodeId,
            String inputId,
            String reason,
            Instant at) {
        var normalizedReason = requireAmendment(reason);
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId 不能为空白");
        }
        var taskEntity = requireOwnedTask(tenantId, userId, taskId);
        var amendmentInput = requireAmendmentInput(tenantId, userId, taskId, inputId);
        if (amendmentInput.getConsumedAt() != null) {
            return List.of();
        }
        var task = taskEntity.getTask();
        if (task.terminal()
                || task.status() == Task.Status.PAUSING
                || task.status() == Task.Status.CANCELING
                || task.currentPlanId() == null) {
            throw new IllegalStateException("只有非终态 planned Task 可以调整节点步骤");
        }
        plans.findCurrentForUpdate(
                        tenantId.value(),
                        taskId.value(),
                        task.currentPlanId(),
                        task.currentPlanRevision())
                .orElseThrow(() -> stale("Task current plan 不存在"));
        var node =
                nodes.findNodeForUpdate(
                                tenantId.value(),
                                taskId.value(),
                                task.currentPlanId(),
                                task.currentPlanRevision(),
                                nodeId.trim())
                        .orElseThrow(() -> new IllegalArgumentException("TaskNode 不存在: " + nodeId));
        if (TaskPlan.Status.COMPLETED.name().equals(node.getStatus())) {
            throw new IllegalStateException("已完成 TaskNode 的步骤历史不可改写");
        }
        var sourceCommand =
                sourceCommand(
                        tenantId,
                        nodes.findPlanNodesForUpdate(
                                tenantId.value(),
                                taskId.value(),
                                task.currentPlanId(),
                                task.currentPlanRevision()),
                        node.getNodeId());
        var anchor =
                executions
                        .findForUpdate(tenantId.value(), sourceCommand.executionId().value())
                        .orElseThrow(() -> stale("TaskNode Execution 不存在"));
        var fencingToken = closeNodeExecution(tenantId, node, normalizedReason, at);
        cancelExecutorPlans(tenantId, taskId, node.getNodeId(), at);
        var parameters = new LinkedHashMap<>(node.getClarifiedParameters());
        parameters.put("executorPlanAmendment", normalizedReason);
        node.setClarifiedParameters(Map.copyOf(parameters));
        if (node.getCurrentAttempt() > 0) {
            node.setStatus(TaskPlan.Status.RETRYABLE.name());
            node.setFailure("局部执行计划已调整: " + normalizedReason);
        } else {
            node.setStatus(TaskPlan.Status.PENDING.name());
            node.setFailure(null);
        }
        nodes.saveAndFlush(node);
        var ready =
                task.withRuntime(
                        Task.Status.READY,
                        task.owner(),
                        task.budgetUsage(),
                        task.checkpoint().withAnnotation("executorPlanAmendment", normalizedReason),
                        task.recoveryPoint(),
                        at);
        taskEntity.setTask(ready);
        tasks.saveAndFlush(taskEntity);
        consumeInput(amendmentInput, at);
        appendEventsAndOutbox(
                List.of(
                        taskAmendedEvent(
                                ready,
                                anchor.getExecution(),
                                userId,
                                "EXECUTOR_PLAN",
                                normalizedReason,
                                inputId,
                                at)),
                fencingToken,
                taskId);
        return materializeLocked(tenantId, taskId, sourceCommand, at);
    }

    @Override
    @Transactional
    public PauseRequest requestPause(
            TenantId tenantId,
            UserId userId,
            TaskId taskId,
            String reason,
            Duration ackTimeout,
            Instant at) {
        return requestPause(tenantId, userId, taskId, reason, ackTimeout, PAUSE_PURPOSE_USER, at);
    }

    @Override
    @Transactional
    public PauseRequest requestTakeOver(
            TenantId tenantId,
            UserId userId,
            TaskId taskId,
            String reason,
            Duration ackTimeout,
            Instant at) {
        return requestPause(
                tenantId, userId, taskId, reason, ackTimeout, PAUSE_PURPOSE_TAKE_OVER, at);
    }

    private PauseRequest requestPause(
            TenantId tenantId,
            UserId userId,
            TaskId taskId,
            String reason,
            Duration ackTimeout,
            String purpose,
            Instant at) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("暂停原因不能为空白");
        }
        if (ackTimeout == null || ackTimeout.isZero() || ackTimeout.isNegative()) {
            throw new IllegalArgumentException("pause ACK timeout 必须为正数");
        }
        var taskEntity = requireOwnedTask(tenantId, userId, taskId);
        var task = taskEntity.getTask();
        var takeOver = PAUSE_PURPOSE_TAKE_OVER.equals(purpose);
        if (takeOver && !task.contract().takeoverPolicy().humanTakeoverAllowed()) {
            throw new IllegalStateException("执行合同禁止人工接管");
        }
        if (task.owner().kind() == Task.OwnerKind.HUMAN) {
            if (takeOver
                    && task.owner().ownerId().equals(userId.value())
                    && task.status() == Task.Status.PAUSED
                    && PAUSE_PURPOSE_TAKE_OVER.equals(
                            Objects.toString(
                                    task.checkpoint().annotations().get(PAUSE_PURPOSE), ""))) {
                return pauseRequest(task);
            }
            throw new IllegalStateException("Task 已由其他 Human owner 持有");
        }
        if (task.status() == Task.Status.PAUSING) {
            var currentPurpose =
                    Objects.toString(task.checkpoint().annotations().get(PAUSE_PURPOSE), "");
            if (currentPurpose.isBlank()) {
                throw stale("PAUSING Task 缺少 pausePurpose");
            }
            if (!currentPurpose.equals(purpose)) {
                throw new IllegalStateException("普通暂停正在收敛，不能静默升级为人工接管");
            }
            if (takeOver
                    && !userId.value()
                            .equals(
                                    Objects.toString(
                                            task.checkpoint().annotations().get(TAKE_OVER_USER_ID),
                                            ""))) {
                throw new IllegalStateException("人工接管请求属于其他用户");
            }
            return pauseRequest(task);
        }
        if (task.status() == Task.Status.PAUSED) {
            if (takeOver) {
                return takeOverPaused(taskEntity, userId, reason.trim(), ackTimeout, at);
            }
            if (task.checkpoint().annotations().containsKey(PAUSE_REQUEST_ID)) {
                return pauseRequest(task);
            }
            throw new IllegalStateException("当前 Task 状态不可请求暂停: " + task.status());
        }
        if (task.terminal() || task.status() == Task.Status.CANCELING) {
            throw new IllegalStateException("当前 Task 状态不可请求暂停: " + task.status());
        }
        var targetIds = new ArrayList<String>();
        TaskExecutionEntity eventAnchor = null;
        var fencingToken = 0L;
        var normalizedReason = reason.trim();
        if (task.currentPlanId() != null) {
            plans.findCurrentForUpdate(
                            tenantId.value(),
                            taskId.value(),
                            task.currentPlanId(),
                            task.currentPlanRevision())
                    .orElseThrow(() -> stale("Task current plan 不存在"));
            var nodeEntities =
                    nodes.findPlanNodesForUpdate(
                            tenantId.value(),
                            taskId.value(),
                            task.currentPlanId(),
                            task.currentPlanRevision());
            for (var nodeEntity : nodeEntities) {
                if (nodeEntity.getCurrentExecutionId() == null) {
                    continue;
                }
                var executionEntity =
                        executions
                                .findForUpdate(tenantId.value(), nodeEntity.getCurrentExecutionId())
                                .orElseThrow(() -> stale("TaskNode current Execution 不存在"));
                if (eventAnchor == null) {
                    eventAnchor = executionEntity;
                }
                if (!executionEntity.getExecution().terminal()) {
                    targetIds.add(nodeEntity.getCurrentExecutionId());
                    fencingToken =
                            Math.max(
                                    fencingToken,
                                    closeDispatchAuthority(
                                            tenantId,
                                            nodeEntity.getCurrentExecutionId(),
                                            normalizedReason,
                                            at));
                }
            }
        } else if (task.currentRootExecutionId() != null) {
            eventAnchor =
                    executions
                            .findForUpdate(tenantId.value(), task.currentRootExecutionId().value())
                            .orElseThrow(() -> stale("Task current root Execution 不存在"));
            if (!eventAnchor.getExecution().terminal()) {
                targetIds.add(task.currentRootExecutionId().value());
                fencingToken =
                        closeDispatchAuthority(
                                tenantId,
                                task.currentRootExecutionId().value(),
                                normalizedReason,
                                at);
            }
        }
        if (eventAnchor == null || targetIds.isEmpty()) {
            throw new IllegalStateException("Task 没有可暂停的 active Execution");
        }

        var requestId = "pause:" + UUID.randomUUID();
        var deadlineAt = at.plus(ackTimeout);
        var pausing =
                task.transitionTo(
                        Task.Status.PAUSING,
                        normalizedReason,
                        new Task.Actor(Task.OwnerKind.HUMAN, userId.value()),
                        task.owner(),
                        task.recoveryPoint(),
                        at);
        var pauseAnnotations = new LinkedHashMap<String, Object>();
        pauseAnnotations.put(PAUSE_REQUEST_ID, requestId);
        pauseAnnotations.put("pauseReason", normalizedReason);
        pauseAnnotations.put("pauseRequestedAt", at.toString());
        pauseAnnotations.put(PAUSE_DEADLINE_AT, deadlineAt.toString());
        pauseAnnotations.put(PAUSE_TARGETS, List.copyOf(targetIds));
        pauseAnnotations.put(PAUSE_ACKS, Map.of());
        pauseAnnotations.put(PAUSE_PURPOSE, purpose);
        if (takeOver) {
            pauseAnnotations.put(TAKE_OVER_USER_ID, userId.value());
        }
        pausing =
                pausing.withRuntime(
                        Task.Status.PAUSING,
                        task.owner(),
                        pausing.budgetUsage(),
                        pausing.checkpoint().withAnnotations(Map.copyOf(pauseAnnotations)),
                        pausing.recoveryPoint(),
                        at);
        taskEntity.setTask(pausing);
        tasks.saveAndFlush(taskEntity);
        dispatches.flush();
        appendEventsAndOutbox(
                List.of(
                        taskPauseEvent(
                                pausing,
                                eventAnchor.getExecution(),
                                userId,
                                normalizedReason,
                                "REQUESTED",
                                requestId,
                                at)),
                fencingToken,
                taskId);
        return new PauseRequest(
                pausing,
                requestId,
                deadlineAt,
                targetIds.stream().map(ExecutionId::new).toList(),
                true);
    }

    private PauseRequest takeOverPaused(
            TaskRootEntity taskEntity,
            UserId userId,
            String reason,
            Duration ackTimeout,
            Instant at) {
        var task = taskEntity.getTask();
        var annotations = task.checkpoint().annotations();
        var requestId = Objects.toString(annotations.get(PAUSE_REQUEST_ID), "");
        var deadlineText = Objects.toString(annotations.get(PAUSE_DEADLINE_AT), "");
        var targets =
                stringList(annotations.get(PAUSE_TARGETS)).stream().map(ExecutionId::new).toList();
        if (requestId.isBlank()) {
            requestId = "take-over:" + UUID.randomUUID();
        }
        var deadlineAt = deadlineText.isBlank() ? at.plus(ackTimeout) : Instant.parse(deadlineText);
        var eventAnchor = pausedEventAnchor(task);
        var previousOwner = task.owner();
        var humanOwner = new Task.Owner(Task.OwnerKind.HUMAN, userId.value());
        var checkpoint =
                task.checkpoint()
                        .withAnnotations(
                                Map.of(
                                        PAUSE_REQUEST_ID,
                                        requestId,
                                        PAUSE_DEADLINE_AT,
                                        deadlineAt.toString(),
                                        PAUSE_TARGETS,
                                        targets.stream().map(ExecutionId::value).toList(),
                                        PAUSE_ACKS,
                                        pauseAcks(task),
                                        PAUSE_PURPOSE,
                                        PAUSE_PURPOSE_TAKE_OVER,
                                        TAKE_OVER_USER_ID,
                                        userId.value(),
                                        PAUSE_RESUME_MODE,
                                        "FRESH_ATTEMPT",
                                        "freshAttemptRequired",
                                        true,
                                        "pauseReason",
                                        reason,
                                        "takeOverCompletedAt",
                                        at.toString()));
        var takenOver =
                task.withRuntime(
                        Task.Status.PAUSED,
                        humanOwner,
                        task.budgetUsage(),
                        checkpoint,
                        task.recoveryPoint(),
                        at);
        taskEntity.setTask(takenOver);
        tasks.saveAndFlush(taskEntity);
        appendEventsAndOutbox(
                List.of(
                        ownershipTransferredEvent(
                                takenOver,
                                eventAnchor.getExecution(),
                                userId,
                                previousOwner,
                                humanOwner,
                                reason,
                                false,
                                "ownership-taken-over:" + requestId,
                                at)),
                0,
                task.taskId());
        return new PauseRequest(takenOver, requestId, deadlineAt, targets, true);
    }

    private TaskExecutionEntity pausedEventAnchor(Task task) {
        TaskExecutionEntity eventAnchor = null;
        if (task.currentPlanId() != null) {
            var nodeEntities =
                    nodes.findPlanNodesForUpdate(
                            task.tenantId().value(),
                            task.taskId().value(),
                            task.currentPlanId(),
                            task.currentPlanRevision());
            for (var node : nodeEntities) {
                if (node.getCurrentExecutionId() != null) {
                    eventAnchor =
                            executions
                                    .findForUpdate(
                                            task.tenantId().value(), node.getCurrentExecutionId())
                                    .orElseThrow(
                                            () -> stale("PAUSED TaskNode current Execution 不存在"));
                    break;
                }
            }
        } else if (task.currentRootExecutionId() != null) {
            eventAnchor =
                    executions
                            .findForUpdate(
                                    task.tenantId().value(), task.currentRootExecutionId().value())
                            .orElseThrow(() -> stale("PAUSED TASK_ROOT Execution 不存在"));
        }
        return requireCancellationAnchor(task.tenantId(), task, eventAnchor);
    }

    @Override
    @Transactional
    public Task acknowledgePause(PauseAckCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        var taskEntity =
                tasks.findForUpdate(command.tenantId().value(), command.taskId().value())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Task 不存在: " + command.taskId().value()));
        var task = taskEntity.getTask();
        if (task.status() != Task.Status.PAUSING) {
            return task;
        }
        var request = pauseRequest(task);
        if (!request.requestId().equals(command.requestId())) {
            throw stale("pause ACK requestId 已过期");
        }
        var executionId = command.executionId().value();
        var targetIds = request.targets().stream().map(ExecutionId::value).toList();
        if (!targetIds.contains(executionId)) {
            throw stale("pause ACK execution 不属于请求 targets");
        }
        var acks = pauseAcks(task);
        if (acks.containsKey(executionId)) {
            return task;
        }
        var execution =
                executions
                        .findForUpdate(command.tenantId().value(), executionId)
                        .orElseThrow(() -> stale("pause ACK Execution 不存在"))
                        .getExecution();
        if (!command.taskId().equals(execution.taskId())) {
            throw stale("pause ACK Execution 绑定了不同 Task");
        }
        var stateSaved =
                command.stateSaved()
                        && execution.stateSlotId().equals(command.stateSlotId())
                        && AGENT_STATE_SCHEMA.equals(command.stateSchema());
        var ack = new LinkedHashMap<String, Object>();
        ack.put("stateSaved", stateSaved);
        ack.put("stateSlotId", Objects.toString(command.stateSlotId(), ""));
        ack.put("stateSchema", Objects.toString(command.stateSchema(), ""));
        ack.put(
                "stateSavedAt",
                command.stateSavedAt() == null ? "" : command.stateSavedAt().toString());
        ack.put("acknowledgedAt", command.at().toString());
        if (!stateSaved) {
            ack.put("failure", Objects.toString(command.failure(), "AgentState save failed"));
        }
        acks.put(executionId, Map.copyOf(ack));
        var checkpoint = task.checkpoint().withAnnotation(PAUSE_ACKS, Map.copyOf(acks));
        task =
                task.withRuntime(
                        Task.Status.PAUSING,
                        task.owner(),
                        task.budgetUsage(),
                        checkpoint,
                        task.recoveryPoint(),
                        command.at());
        taskEntity.setTask(task);
        tasks.saveAndFlush(taskEntity);
        return acks.keySet().containsAll(targetIds)
                ? settlePause(taskEntity, false, command.at())
                : task;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PausingTask> findPausing(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit 必须在 1..100");
        }
        return tasks
                .findByStatusOrderByIdAsc(Task.Status.PAUSING.name(), PageRequest.of(0, limit))
                .stream()
                .map(
                        entity ->
                                new PausingTask(
                                        entity.getTask().tenantId(),
                                        entity.getTask().taskId(),
                                        pauseRequest(entity.getTask()).deadlineAt()))
                .toList();
    }

    @Override
    @Transactional
    public boolean finalizeTimedOutPause(TenantId tenantId, TaskId taskId, Instant at) {
        var taskEntity =
                tasks.findForUpdate(tenantId.value(), taskId.value())
                        .orElseThrow(
                                () -> new IllegalArgumentException("Task 不存在: " + taskId.value()));
        var task = taskEntity.getTask();
        if (task.status() != Task.Status.PAUSING) {
            return false;
        }
        var request = pauseRequest(task);
        if (at.isBefore(request.deadlineAt())) {
            return false;
        }
        settlePause(taskEntity, true, at);
        return true;
    }

    @Override
    @Transactional
    public ResumeResult resumePaused(TenantId tenantId, UserId userId, TaskId taskId, Instant at) {
        var taskEntity = requireOwnedTask(tenantId, userId, taskId);
        var task = taskEntity.getTask();
        if ((task.status() == Task.Status.READY || task.status() == Task.Status.RUNNING)
                && task.owner().kind() == Task.OwnerKind.ASSISTANT
                && task.checkpoint().annotations().containsKey("pauseResumedAt")) {
            return currentResumeResult(task);
        }
        if (task.owner().kind() == Task.OwnerKind.HUMAN) {
            throw new IllegalStateException("Human owner 持有的 Task 必须通过 hand-back 交回 Assistant");
        }
        if (task.owner().kind() != Task.OwnerKind.ASSISTANT) {
            throw new IllegalStateException("只有 Owner Assistant 可以恢复暂停 Task");
        }
        if (task.status() != Task.Status.PAUSED) {
            throw new IllegalStateException("只有 durable pause 完成的 Task 可以恢复");
        }
        var mode = Objects.toString(task.checkpoint().annotations().get(PAUSE_RESUME_MODE), "");
        if (!"SAME_ATTEMPT".equals(mode) && !"FRESH_ATTEMPT".equals(mode)) {
            throw new IllegalStateException("PAUSED Task 缺少 durable pause resume mode");
        }
        return resumePausedLocked(taskEntity, mode, userId, "恢复用户暂停的 Task", at);
    }

    @Override
    @Transactional
    public ResumeResult handBack(TenantId tenantId, UserId userId, TaskId taskId, Instant at) {
        var taskEntity = requireOwnedTask(tenantId, userId, taskId);
        var task = taskEntity.getTask();
        var currentPauseRequestId =
                Objects.toString(task.checkpoint().annotations().get(PAUSE_REQUEST_ID), "");
        var completedHandBackRequestId =
                Objects.toString(task.checkpoint().annotations().get(HAND_BACK_REQUEST_ID), "");
        if (task.owner().kind() == Task.OwnerKind.ASSISTANT
                && !currentPauseRequestId.isBlank()
                && currentPauseRequestId.equals(completedHandBackRequestId)
                && task.checkpoint().annotations().containsKey(HAND_BACK_COMPLETED_AT)) {
            return currentResumeResult(task);
        }
        if (task.status() != Task.Status.PAUSED
                || task.owner().kind() != Task.OwnerKind.HUMAN
                || !task.owner().ownerId().equals(userId.value())) {
            throw new IllegalStateException("只有当前用户持有的 PAUSED Task 可以交回 Assistant");
        }
        if (!task.contract().takeoverPolicy().handBackAllowed()) {
            throw new IllegalStateException("执行合同禁止交回 Assistant");
        }
        var responsibleOwner = task.contract().responsibleOwner();
        if (!"ASSISTANT".equals(responsibleOwner.ownerType())) {
            throw new IllegalStateException("执行合同缺少唯一 Owner Assistant");
        }
        var request = pauseRequest(task);
        var eventAnchor = pausedEventAnchor(task);
        var previousOwner = task.owner();
        var assistantOwner = new Task.Owner(Task.OwnerKind.ASSISTANT, responsibleOwner.ownerId());
        var checkpoint =
                task.checkpoint()
                        .withAnnotations(
                                Map.of(
                                        PAUSE_RESUME_MODE,
                                        "FRESH_ATTEMPT",
                                        "freshAttemptRequired",
                                        true,
                                        HAND_BACK_COMPLETED_AT,
                                        at.toString(),
                                        HAND_BACK_REQUEST_ID,
                                        request.requestId(),
                                        "handBackUserId",
                                        userId.value()));
        var handedBack =
                task.withRuntime(
                        Task.Status.PAUSED,
                        assistantOwner,
                        task.budgetUsage(),
                        checkpoint,
                        task.recoveryPoint(),
                        at);
        taskEntity.setTask(handedBack);
        tasks.saveAndFlush(taskEntity);
        var resumed =
                resumePausedLocked(
                        taskEntity, "FRESH_ATTEMPT", userId, "用户将 Task 交回 Owner Assistant", at);
        appendEventsAndOutbox(
                List.of(
                        ownershipTransferredEvent(
                                resumed.task(),
                                eventAnchor.getExecution(),
                                userId,
                                previousOwner,
                                assistantOwner,
                                "用户将 Task 交回 Owner Assistant",
                                true,
                                "ownership-handed-back:" + request.requestId(),
                                at)),
                0,
                taskId);
        return resumed;
    }

    private ResumeResult currentResumeResult(Task task) {
        var rootPending =
                task.status() == Task.Status.READY
                        && task.currentRootExecutionId() != null
                        && dispatches
                                .findActiveForUpdate(
                                        task.tenantId().value(),
                                        task.currentRootExecutionId().value())
                                .map(
                                        entity ->
                                                entity.getDispatch().status()
                                                        == TaskDispatch.Status.PENDING)
                                .orElse(false);
        return new ResumeResult(task, List.of(), rootPending);
    }

    private ResumeResult resumePausedLocked(
            TaskRootEntity taskEntity, String mode, UserId userId, String reason, Instant at) {
        var task = taskEntity.getTask();
        if (task.currentRootExecutionId() != null) {
            return resumePausedRoot(taskEntity, mode, userId, reason, at);
        }
        if (task.currentPlanId() == null) {
            throw new IllegalStateException("PAUSED Task 缺少 current plan/root identity");
        }
        plans.findCurrentForUpdate(
                        task.tenantId().value(),
                        task.taskId().value(),
                        task.currentPlanId(),
                        task.currentPlanRevision())
                .orElseThrow(() -> stale("Task current plan 不存在"));
        var nodeEntities =
                nodes.findPlanNodesForUpdate(
                        task.tenantId().value(),
                        task.taskId().value(),
                        task.currentPlanId(),
                        task.currentPlanRevision());
        for (var node : nodeEntities) {
            if (!TaskPlan.Status.PAUSED.name().equals(node.getStatus())
                    && !TaskPlan.Status.RETRYABLE.name().equals(node.getStatus())) {
                continue;
            }
            node.setStatus(
                    "SAME_ATTEMPT".equals(mode)
                            ? TaskPlan.Status.READY.name()
                            : TaskPlan.Status.RETRYABLE.name());
            nodes.save(node);
        }
        var ready = resumedTask(task, userId, reason, at);
        taskEntity.setTask(ready);
        tasks.saveAndFlush(taskEntity);
        nodes.flush();
        var source =
                sourceCommand(task.tenantId(), nodeEntities, nodeEntities.getFirst().getNodeId());
        return new ResumeResult(
                ready, materializeLocked(task.tenantId(), task.taskId(), source, at), false);
    }

    private ResumeResult resumePausedRoot(
            TaskRootEntity taskEntity, String mode, UserId userId, String reason, Instant at) {
        var task = taskEntity.getTask();
        var currentExecutionId = task.currentRootExecutionId();
        var currentEntity =
                executions
                        .findForUpdate(task.tenantId().value(), currentExecutionId.value())
                        .orElseThrow(() -> stale("PAUSED Task current root Execution 不存在"));
        var current = currentEntity.getExecution();
        var ack = pauseAcks(task).get(currentExecutionId.value());
        var stateAvailable = pauseAckSaved(ack);
        var stateCompatible =
                ack != null
                        && current.stateSlotId()
                                .equals(Objects.toString(ack.get("stateSlotId"), ""))
                        && AGENT_STATE_SCHEMA.equals(Objects.toString(ack.get("stateSchema"), ""));
        var decision =
                "FRESH_ATTEMPT".equals(mode)
                        ? ExecutionResumePolicy.Decision.FRESH_ATTEMPT
                        : new ExecutionResumePolicy()
                                .decide(
                                        new ExecutionResumePolicy.Request(
                                                current,
                                                current.ownerSnapshot().equals(task.owner()),
                                                true,
                                                stateAvailable,
                                                stateCompatible));
        if (decision == ExecutionResumePolicy.Decision.REJECT) {
            throw stale("current TASK_ROOT Execution 状态不允许恢复");
        }

        var sourceCommand = currentEntity.getCommand().withoutDispatch(null, at);
        var ready = resumedTask(task, userId, reason, at);
        final TaskExecutionEntity resumedEntity;
        final Execution resumedExecution;
        final AssistantCommand resumedCommand;
        final long generation;
        if (decision == ExecutionResumePolicy.Decision.SAME_ATTEMPT) {
            resumedEntity = currentEntity;
            resumedExecution = current.withStatus(Execution.Status.READY, at);
            resumedCommand = sourceCommand.asResume(at);
            generation =
                    dispatches
                            .findFirstByTenantIdAndExecutionIdOrderByIdDesc(
                                    task.tenantId().value(), currentExecutionId.value())
                            .map(previous -> previous.getDispatch().generation() + 1)
                            .orElse(1L);
        } else {
            currentEntity.setExecution(current.withStatus(Execution.Status.SUPERSEDED, at));
            executions.save(currentEntity);
            var nextExecutionId = new ExecutionId("execution:" + UUID.randomUUID());
            var nextSessionId = new SessionId("session:" + UUID.randomUUID());
            resumedCommand =
                    sourceCommand.newExecution(
                            nextExecutionId,
                            nextSessionId,
                            new com.xuejiai.aaf.framework.intelligent.shared.id.StableId.RunId(
                                    "run:" + UUID.randomUUID()),
                            null,
                            at);
            resumedExecution =
                    new Execution(
                            task.tenantId(),
                            task.userId(),
                            task.conversationId(),
                            task.taskId(),
                            null,
                            null,
                            null,
                            nextExecutionId,
                            nextSessionId,
                            resumedCommand.runId(),
                            resumedCommand.correlationId(),
                            current.parentExecutionId(),
                            current.executionId(),
                            Execution.Scope.TASK_ROOT,
                            current.attemptNo() + 1,
                            nextExecutionId.value(),
                            Execution.Status.READY,
                            Execution.PromotionState.INELIGIBLE,
                            0,
                            task.owner(),
                            current.consecutiveFailures(),
                            at,
                            at);
            resumedEntity = new TaskExecutionEntity();
            ready = ready.withCurrentRoot(nextExecutionId, current.attemptNo() + 1, at);
            generation = 1;
        }
        resumedEntity.setExecution(resumedExecution);
        resumedEntity.setCommand(resumedCommand);
        executions.save(resumedEntity);

        var dispatch =
                new TaskDispatch(
                        "dispatch:" + UUID.randomUUID(),
                        task.tenantId(),
                        resumedExecution.executionId(),
                        TaskDispatch.Status.PENDING,
                        at,
                        null,
                        null,
                        generation,
                        dispatches.nextFence(),
                        0,
                        null,
                        0,
                        at,
                        at);
        var dispatchEntity = new TaskDispatchEntity();
        dispatchEntity.setDispatch(dispatch);
        dispatches.save(dispatchEntity);
        taskEntity.setTask(ready);
        tasks.saveAndFlush(taskEntity);
        executions.flush();
        dispatches.flush();
        return new ResumeResult(ready, List.of(), true);
    }

    private static Task resumedTask(Task task, UserId userId, String reason, Instant at) {
        var ready =
                task.transitionTo(
                        Task.Status.READY,
                        reason,
                        new Task.Actor(Task.OwnerKind.HUMAN, userId.value()),
                        task.owner(),
                        task.recoveryPoint(),
                        at);
        return ready.withRuntime(
                Task.Status.READY,
                ready.owner(),
                ready.budgetUsage(),
                ready.checkpoint().withAnnotation("pauseResumedAt", at.toString()),
                ready.recoveryPoint(),
                at);
    }

    private Task settlePause(TaskRootEntity taskEntity, boolean timedOut, Instant at) {
        var task = taskEntity.getTask();
        var request = pauseRequest(task);
        var acks = pauseAcks(task);
        if (timedOut) {
            for (var target : request.targets()) {
                acks.computeIfAbsent(
                        target.value(),
                        ignored ->
                                Map.of(
                                        "stateSaved",
                                        false,
                                        "stateSlotId",
                                        target.value(),
                                        "stateSchema",
                                        AGENT_STATE_SCHEMA,
                                        "stateSavedAt",
                                        "",
                                        "acknowledgedAt",
                                        at.toString(),
                                        "failure",
                                        "pause ACK deadline exceeded"));
            }
            task =
                    task.withRuntime(
                            Task.Status.PAUSING,
                            task.owner(),
                            task.budgetUsage(),
                            task.checkpoint().withAnnotation(PAUSE_ACKS, Map.copyOf(acks)),
                            task.recoveryPoint(),
                            at);
            taskEntity.setTask(task);
            tasks.saveAndFlush(taskEntity);
        }
        var allSaved =
                !timedOut
                        && request.targets().stream()
                                .map(ExecutionId::value)
                                .allMatch(id -> pauseAckSaved(acks.get(id)));
        var purpose = Objects.toString(task.checkpoint().annotations().get(PAUSE_PURPOSE), "");
        if (!PAUSE_PURPOSE_USER.equals(purpose) && !PAUSE_PURPOSE_TAKE_OVER.equals(purpose)) {
            throw stale("PAUSING Task pausePurpose 非法");
        }
        var takeOver = PAUSE_PURPOSE_TAKE_OVER.equals(purpose);
        var mode = takeOver || !allSaved ? "FRESH_ATTEMPT" : "SAME_ATTEMPT";
        TaskExecutionEntity eventAnchor = null;
        if (task.currentPlanId() != null) {
            var nodeEntities =
                    nodes.findPlanNodesForUpdate(
                            task.tenantId().value(),
                            task.taskId().value(),
                            task.currentPlanId(),
                            task.currentPlanRevision());
            for (var node : nodeEntities) {
                if (node.getCurrentExecutionId() == null
                        || !request.targets().stream()
                                .map(ExecutionId::value)
                                .toList()
                                .contains(node.getCurrentExecutionId())) {
                    continue;
                }
                var executionEntity =
                        executions
                                .findForUpdate(
                                        task.tenantId().value(), node.getCurrentExecutionId())
                                .orElseThrow(() -> stale("pause target Execution 不存在"));
                if (eventAnchor == null) {
                    eventAnchor = executionEntity;
                }
                if (!executionEntity.getExecution().terminal()) {
                    executionEntity.setExecution(
                            executionEntity.getExecution().withStatus(Execution.Status.PAUSED, at));
                    executions.save(executionEntity);
                }
                if (!TaskPlan.Status.COMPLETED.name().equals(node.getStatus())
                        && !TaskPlan.Status.FAILED.name().equals(node.getStatus())
                        && !TaskPlan.Status.CANCELED.name().equals(node.getStatus())) {
                    node.setStatus(
                            allSaved
                                    ? TaskPlan.Status.PAUSED.name()
                                    : TaskPlan.Status.RETRYABLE.name());
                    node.setFailure(allSaved ? null : "暂停状态保存失败，恢复时创建 fresh attempt");
                    nodes.save(node);
                }
            }
        } else if (task.currentRootExecutionId() != null
                && request.targets().contains(task.currentRootExecutionId())) {
            var executionEntity =
                    executions
                            .findForUpdate(
                                    task.tenantId().value(), task.currentRootExecutionId().value())
                            .orElseThrow(() -> stale("pause target TASK_ROOT Execution 不存在"));
            eventAnchor = executionEntity;
            if (!executionEntity.getExecution().terminal()) {
                executionEntity.setExecution(
                        executionEntity.getExecution().withStatus(Execution.Status.PAUSED, at));
                executions.save(executionEntity);
            }
        }
        eventAnchor = requireCancellationAnchor(task.tenantId(), task, eventAnchor);
        var pauseReason =
                Objects.toString(task.checkpoint().annotations().get("pauseReason"), "用户暂停 Task");
        var transferUser = task.userId();
        var nextOwner = task.owner();
        if (takeOver) {
            var takeOverUserId =
                    Objects.toString(task.checkpoint().annotations().get(TAKE_OVER_USER_ID), "");
            if (takeOverUserId.isBlank() || !task.userId().value().equals(takeOverUserId)) {
                throw stale("take-over user identity 不一致");
            }
            transferUser = new UserId(takeOverUserId);
            nextOwner = new Task.Owner(Task.OwnerKind.HUMAN, takeOverUserId);
        }
        var previousOwner = task.owner();
        var paused =
                task.transitionTo(
                        Task.Status.PAUSED,
                        timedOut ? "暂停 ACK 超时，降级 fresh attempt" : "暂停 ACK 已收敛",
                        new Task.Actor(Task.OwnerKind.SYSTEM, "pause-finalizer"),
                        nextOwner,
                        task.recoveryPoint(),
                        at);
        var checkpointAnnotations = new LinkedHashMap<String, Object>();
        checkpointAnnotations.put(PAUSE_RESUME_MODE, mode);
        checkpointAnnotations.put("freshAttemptRequired", takeOver || !allSaved);
        checkpointAnnotations.put("pauseCompletedAt", at.toString());
        checkpointAnnotations.put("pauseTimedOut", timedOut);
        if (takeOver) {
            checkpointAnnotations.put("takeOverCompletedAt", at.toString());
        }
        paused =
                paused.withRuntime(
                        Task.Status.PAUSED,
                        paused.owner(),
                        paused.budgetUsage(),
                        paused.checkpoint().withAnnotations(Map.copyOf(checkpointAnnotations)),
                        paused.recoveryPoint(),
                        at);
        taskEntity.setTask(paused);
        tasks.saveAndFlush(taskEntity);
        nodes.flush();
        executions.flush();
        var transitionEvents = new ArrayList<ExecutionEvent>();
        transitionEvents.add(
                taskPauseEvent(
                        paused,
                        eventAnchor.getExecution(),
                        transferUser,
                        pauseReason,
                        "COMPLETED",
                        request.requestId(),
                        at));
        if (takeOver) {
            transitionEvents.add(
                    ownershipTransferredEvent(
                            paused,
                            eventAnchor.getExecution(),
                            transferUser,
                            previousOwner,
                            paused.owner(),
                            pauseReason,
                            false,
                            "ownership-taken-over:" + request.requestId(),
                            at));
        }
        appendEventsAndOutbox(transitionEvents, 0, task.taskId());
        return paused;
    }

    private static PauseRequest pauseRequest(Task task) {
        var annotations = task.checkpoint().annotations();
        var requestId = Objects.toString(annotations.get(PAUSE_REQUEST_ID), "");
        var deadline = Objects.toString(annotations.get(PAUSE_DEADLINE_AT), "");
        var purpose = Objects.toString(annotations.get(PAUSE_PURPOSE), "");
        var targets =
                stringList(annotations.get(PAUSE_TARGETS)).stream().map(ExecutionId::new).toList();
        if (requestId.isBlank()
                || deadline.isBlank()
                || (!PAUSE_PURPOSE_USER.equals(purpose)
                        && !PAUSE_PURPOSE_TAKE_OVER.equals(purpose))) {
            throw new IllegalStateException("PAUSING Task 缺少 durable pause request checkpoint");
        }
        return new PauseRequest(task, requestId, Instant.parse(deadline), targets, false);
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream().map(String::valueOf).toList();
    }

    private static LinkedHashMap<String, Map<String, Object>> pauseAcks(Task task) {
        var result = new LinkedHashMap<String, Map<String, Object>>();
        var raw = task.checkpoint().annotations().get(PAUSE_ACKS);
        if (raw instanceof Map<?, ?> values) {
            values.forEach(
                    (key, value) -> {
                        if (value instanceof Map<?, ?> ack) {
                            var normalized = new LinkedHashMap<String, Object>();
                            ack.forEach(
                                    (ackKey, ackValue) ->
                                            normalized.put(String.valueOf(ackKey), ackValue));
                            result.put(String.valueOf(key), Map.copyOf(normalized));
                        }
                    });
        }
        return result;
    }

    private static boolean pauseAckSaved(Map<String, Object> ack) {
        return ack != null && Boolean.TRUE.equals(ack.get("stateSaved"));
    }

    @Override
    @Transactional
    public Task requestCancellation(
            TenantId tenantId, UserId userId, TaskId taskId, String reason, Instant at) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("取消原因不能为空白");
        }
        var normalizedReason = reason.trim();
        var taskEntity =
                tasks.findForUpdate(tenantId.value(), taskId.value())
                        .orElseThrow(
                                () -> new IllegalArgumentException("Task 不存在: " + taskId.value()));
        var task = taskEntity.getTask();
        if (!task.userId().equals(userId)) {
            throw new IllegalArgumentException("Task 不属于当前用户");
        }
        if (task.terminal() || task.status() == Task.Status.CANCELING) {
            return task;
        }

        TaskExecutionEntity eventAnchor = null;
        var fencingToken = 0L;
        if (task.currentPlanId() != null) {
            plans.findCurrentForUpdate(
                            tenantId.value(),
                            taskId.value(),
                            task.currentPlanId(),
                            task.currentPlanRevision())
                    .orElseThrow(() -> stale("Task current plan 不存在"));
            var nodeEntities =
                    nodes.findPlanNodesForUpdate(
                            tenantId.value(),
                            taskId.value(),
                            task.currentPlanId(),
                            task.currentPlanRevision());
            for (var nodeEntity : nodeEntities) {
                if (nodeEntity.getCurrentExecutionId() == null) {
                    continue;
                }
                var executionEntity =
                        executions
                                .findForUpdate(tenantId.value(), nodeEntity.getCurrentExecutionId())
                                .orElseThrow(() -> stale("TaskNode current Execution 不存在"));
                if (eventAnchor == null) {
                    eventAnchor = executionEntity;
                }
                fencingToken =
                        Math.max(
                                fencingToken,
                                closeDispatchAuthority(
                                        tenantId,
                                        nodeEntity.getCurrentExecutionId(),
                                        normalizedReason,
                                        at));
            }
        } else if (task.currentRootExecutionId() != null) {
            eventAnchor =
                    executions
                            .findForUpdate(tenantId.value(), task.currentRootExecutionId().value())
                            .orElseThrow(() -> stale("Task current root Execution 不存在"));
            fencingToken =
                    closeDispatchAuthority(
                            tenantId, task.currentRootExecutionId().value(), normalizedReason, at);
        }
        eventAnchor = requireCancellationAnchor(tenantId, task, eventAnchor);

        var human = new Task.Owner(Task.OwnerKind.HUMAN, userId.value());
        var canceling =
                task.transitionTo(
                        Task.Status.CANCELING,
                        normalizedReason,
                        new Task.Actor(Task.OwnerKind.HUMAN, userId.value()),
                        human,
                        task.recoveryPoint(),
                        at);
        canceling =
                canceling.withRuntime(
                        Task.Status.CANCELING,
                        human,
                        canceling.budgetUsage(),
                        canceling
                                .checkpoint()
                                .withAnnotations(
                                        Map.of(
                                                "cancelReason",
                                                normalizedReason,
                                                "cancellationRequestedAt",
                                                at.toString())),
                        canceling.recoveryPoint(),
                        at);
        taskEntity.setTask(canceling);
        taskEntity = tasks.saveAndFlush(taskEntity);
        dispatches.flush();
        appendEventsAndOutbox(
                List.of(
                        taskCancellationEvent(
                                canceling,
                                eventAnchor.getExecution(),
                                userId,
                                normalizedReason,
                                "REQUESTED",
                                at)),
                fencingToken,
                taskId);
        return taskEntity.getTask();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CancelingTask> findCanceling(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit 必须在 1..100");
        }
        return tasks
                .findByStatusOrderByIdAsc(Task.Status.CANCELING.name(), PageRequest.of(0, limit))
                .stream()
                .map(
                        entity ->
                                new CancelingTask(
                                        entity.getTask().tenantId(), entity.getTask().taskId()))
                .toList();
    }

    @Override
    @Transactional
    public boolean finalizeCancellation(TenantId tenantId, TaskId taskId, Instant at) {
        var taskEntity =
                tasks.findForUpdate(tenantId.value(), taskId.value())
                        .orElseThrow(
                                () -> new IllegalArgumentException("Task 不存在: " + taskId.value()));
        var task = taskEntity.getTask();
        if (task.status() == Task.Status.CANCELED) {
            return false;
        }
        if (task.status() != Task.Status.CANCELING) {
            return false;
        }
        var reason =
                Objects.toString(task.checkpoint().annotations().get("cancelReason"), "用户取消 Task");
        var userId = task.userId();
        var canceledEvents = new ArrayList<ExecutionEvent>();
        TaskExecutionEntity eventAnchor = null;
        var fencingToken = 0L;

        if (task.currentPlanId() != null) {
            plans.findCurrentForUpdate(
                            tenantId.value(),
                            taskId.value(),
                            task.currentPlanId(),
                            task.currentPlanRevision())
                    .orElseThrow(() -> stale("Task current plan 不存在"));
            var nodeEntities =
                    nodes.findPlanNodesForUpdate(
                            tenantId.value(),
                            taskId.value(),
                            task.currentPlanId(),
                            task.currentPlanRevision());
            for (var nodeEntity : nodeEntities) {
                if (!TaskPlan.Status.COMPLETED.name().equals(nodeEntity.getStatus())
                        && !TaskPlan.Status.FAILED.name().equals(nodeEntity.getStatus())
                        && !TaskPlan.Status.CANCELED.name().equals(nodeEntity.getStatus())) {
                    nodeEntity.setStatus(TaskPlan.Status.CANCELED.name());
                    nodeEntity.setFailure(reason);
                    nodes.save(nodeEntity);
                }
                cancelExecutorPlans(tenantId, taskId, nodeEntity.getNodeId(), at);
                if (nodeEntity.getCurrentExecutionId() == null) {
                    continue;
                }
                var executionEntity =
                        executions
                                .findForUpdate(tenantId.value(), nodeEntity.getCurrentExecutionId())
                                .orElseThrow(() -> stale("TaskNode current Execution 不存在"));
                if (eventAnchor == null) {
                    eventAnchor = executionEntity;
                }
                var execution = executionEntity.getExecution();
                if (!execution.terminal()) {
                    canceledEvents.add(executionCanceledEvent(task, execution, userId, reason, at));
                    executionEntity.setExecution(
                            execution.withStatus(Execution.Status.CANCELED, at));
                    executions.save(executionEntity);
                }
                fencingToken =
                        Math.max(
                                fencingToken,
                                closeDispatchAuthority(
                                        tenantId, nodeEntity.getCurrentExecutionId(), reason, at));
            }
        } else if (task.currentRootExecutionId() != null) {
            var executionEntity =
                    executions
                            .findForUpdate(tenantId.value(), task.currentRootExecutionId().value())
                            .orElseThrow(() -> stale("Task current root Execution 不存在"));
            eventAnchor = executionEntity;
            var execution = executionEntity.getExecution();
            if (!execution.terminal()) {
                canceledEvents.add(executionCanceledEvent(task, execution, userId, reason, at));
                executionEntity.setExecution(execution.withStatus(Execution.Status.CANCELED, at));
                executions.save(executionEntity);
            }
            fencingToken =
                    closeDispatchAuthority(
                            tenantId, task.currentRootExecutionId().value(), reason, at);
        }
        eventAnchor = requireCancellationAnchor(tenantId, task, eventAnchor);

        var human = new Task.Owner(Task.OwnerKind.HUMAN, userId.value());
        var canceled =
                task.transitionTo(
                        Task.Status.CANCELED,
                        reason,
                        new Task.Actor(Task.OwnerKind.HUMAN, userId.value()),
                        human,
                        task.recoveryPoint(),
                        at);
        canceled =
                canceled.withRuntime(
                        Task.Status.CANCELED,
                        human,
                        canceled.budgetUsage(),
                        canceled.checkpoint()
                                .withAnnotation("cancellationCompletedAt", at.toString()),
                        canceled.recoveryPoint(),
                        at);
        taskEntity.setTask(canceled);
        tasks.saveAndFlush(taskEntity);
        nodes.flush();
        executions.flush();
        dispatches.flush();
        canceledEvents.add(
                taskCancellationEvent(
                        canceled, eventAnchor.getExecution(), userId, reason, "COMPLETED", at));
        appendEventsAndOutbox(canceledEvents, fencingToken, taskId);
        return true;
    }

    private long closeDispatchAuthority(
            TenantId tenantId, String executionId, String reason, Instant at) {
        var dispatchEntity =
                dispatches.findActiveForUpdate(tenantId.value(), executionId).orElse(null);
        if (dispatchEntity == null) {
            return 0;
        }
        var current = dispatchEntity.getDispatch();
        var nextFence = dispatches.nextFence();
        dispatchEntity.setDispatch(
                new TaskDispatch(
                        current.dispatchId(),
                        current.tenantId(),
                        current.executionId(),
                        TaskDispatch.Status.CANCELED,
                        current.nextRunAt(),
                        null,
                        null,
                        current.generation() + 1,
                        nextFence,
                        current.deliveryAttempts(),
                        reason,
                        current.version(),
                        current.createdAt(),
                        at));
        dispatches.save(dispatchEntity);
        return nextFence;
    }

    private TaskExecutionEntity requireCancellationAnchor(
            TenantId tenantId, Task task, TaskExecutionEntity current) {
        if (current != null) {
            return current;
        }
        if (task.originExecutionId() != null) {
            return executions
                    .findForUpdate(tenantId.value(), task.originExecutionId().value())
                    .orElseThrow(() -> stale("Task origin Execution 不存在"));
        }
        throw stale("非终态 Task 缺少可审计的 current/origin Execution");
    }

    private TaskRootEntity requireOwnedTask(TenantId tenantId, UserId userId, TaskId taskId) {
        var entity =
                tasks.findForUpdate(tenantId.value(), taskId.value())
                        .orElseThrow(
                                () -> new IllegalArgumentException("Task 不存在: " + taskId.value()));
        if (!entity.getTask().userId().equals(userId)) {
            throw new IllegalArgumentException("Task 不属于当前用户");
        }
        return entity;
    }

    private AssistantCommand sourceCommand(
            TenantId tenantId, List<TaskNodeEntity> nodeEntities, String preferredNodeId) {
        var preferred =
                nodeEntities.stream()
                        .filter(node -> node.getNodeId().equals(preferredNodeId))
                        .map(TaskNodeEntity::getCurrentExecutionId)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);
        if (preferred != null) {
            return executions
                    .findByTenantIdAndExecutionId(tenantId.value(), preferred)
                    .map(TaskExecutionEntity::getCommand)
                    .orElseThrow(() -> stale("TaskNode current Execution 缺少 command"));
        }
        return nodeEntities.stream()
                .filter(node -> node.getCurrentExecutionId() != null)
                .map(
                        node ->
                                executions
                                        .findByTenantIdAndExecutionId(
                                                tenantId.value(), node.getCurrentExecutionId())
                                        .orElse(null))
                .filter(Objects::nonNull)
                .map(TaskExecutionEntity::getCommand)
                .findFirst()
                .orElseThrow(() -> stale("TaskPlan 缺少可派生新 attempt 的 source command"));
    }

    private long closeNodeExecution(
            TenantId tenantId, TaskNodeEntity node, String reason, Instant at) {
        if (node.getCurrentExecutionId() == null) {
            return 0;
        }
        var executionEntity =
                executions
                        .findForUpdate(tenantId.value(), node.getCurrentExecutionId())
                        .orElseThrow(() -> stale("TaskNode current Execution 不存在"));
        var execution = executionEntity.getExecution();
        if (!execution.terminal()) {
            executionEntity.setExecution(execution.withStatus(Execution.Status.SUPERSEDED, at));
            executions.save(executionEntity);
        }
        var dispatchEntity =
                dispatches
                        .findActiveForUpdate(tenantId.value(), node.getCurrentExecutionId())
                        .orElse(null);
        if (dispatchEntity == null) {
            return 0;
        }
        var fencingToken = dispatchEntity.getDispatch().fencingToken();
        dispatchEntity.setDispatch(
                terminalDispatch(
                        dispatchEntity.getDispatch(), TaskDispatch.Status.CANCELED, reason, at));
        dispatches.save(dispatchEntity);
        return fencingToken;
    }

    private void cancelExecutorPlans(TenantId tenantId, TaskId taskId, String nodeId, Instant at) {
        executorPlans
                .findActiveCandidates(tenantId.value(), taskId.value(), nodeId)
                .forEach(
                        candidate -> {
                            var entity =
                                    executorPlans
                                            .findForUpdate(candidate.getPlanId())
                                            .orElseThrow();
                            var status = ExecutorPlan.Status.valueOf(entity.getStatus());
                            if (status != ExecutorPlan.Status.COMPLETED
                                    && status != ExecutorPlan.Status.FAILED
                                    && status != ExecutorPlan.Status.CANCELLED
                                    && status != ExecutorPlan.Status.REJECTED) {
                                entity.setStatus(ExecutorPlan.Status.CANCELLED.name());
                                entity.setReviewComment("用户调整任务或步骤");
                                entity.setFinishedAt(at);
                                entity.setUpdatedAt(at);
                                executorPlans.save(entity);
                            }
                        });
        executorPlans.flush();
    }

    private TaskPlan replanningPlan(
            TaskPlan current, TaskPlan.TaskNode coordinator, String reason) {
        var nextCoordinator =
                TaskPlan.TaskNode.pending(
                        "coordinator",
                        TaskPlan.TaskNode.Kind.COORDINATOR,
                        current.goal().description() + "\n\n用户调整要求：" + reason,
                        java.util.Set.of(),
                        Map.of(),
                        coordinator.roleKey(),
                        coordinator.skillKey(),
                        coordinator.assistantTarget(),
                        coordinator.modelSelection(),
                        coordinator.maxAttempts());
        var goal =
                new TaskPlan.Goal(
                        "goal",
                        current.goal().description() + "\n\n用户调整要求：" + reason,
                        java.util.Set.of(nextCoordinator.nodeId()),
                        com.xuejiai.aaf.framework.intelligent.assistant.model.TaskPlanDraft
                                .AggregationContract.passThrough(nextCoordinator.nodeId()),
                        null);
        return new TaskPlan(
                current.planId(),
                current.taskId(),
                current.revision() + 1,
                TaskPlan.PlanStatus.FROZEN,
                goal,
                1,
                current.failurePolicy(),
                null,
                0,
                Map.of(nextCoordinator.nodeId(), nextCoordinator));
    }

    private void persistPlanRevision(TenantId tenantId, TaskPlan plan) {
        var planEntity = new TaskPlanEntity();
        planEntity.setTenantId(tenantId.value());
        planEntity.setTaskId(plan.taskId().value());
        planEntity.setPlanId(plan.planId());
        planEntity.setPlanRevision(plan.revision());
        planEntity.setStatus(plan.planStatus().name());
        planEntity.setMaxParallelism(plan.maxParallelism());
        planEntity.setFailurePolicy(plan.failurePolicy().name());
        planEntity.setGraphHash(plan.graphHash());
        planEntity.setGoal(plan.goal());
        plans.save(planEntity);
        for (var node : plan.nodes().values()) {
            var nodeEntity = new TaskNodeEntity();
            nodeEntity.setTenantId(tenantId.value());
            nodeEntity.setTaskId(plan.taskId().value());
            nodeEntity.setPlanId(plan.planId());
            nodeEntity.setPlanRevision(plan.revision());
            nodeEntity.setNodeId(node.nodeId());
            nodeEntity.setStatus(node.status().name());
            nodeEntity.setCurrentExecutionId(null);
            nodeEntity.setCurrentSessionId(null);
            nodeEntity.setCurrentAttempt(0);
            nodeEntity.setDefinition(TaskNodeDefinition.from(node));
            nodeEntity.setClarifiedParameters(node.clarifiedParameters());
            nodes.save(nodeEntity);
        }
        nodes.flush();
        plans.flush();
    }

    private TaskInputEntity requireAmendmentInput(
            TenantId tenantId, UserId userId, TaskId taskId, String inputId) {
        var input =
                inputs.findForUpdate(tenantId.value(), inputId)
                        .orElseThrow(() -> stale("重规划输入不存在: " + inputId));
        if (!input.getTaskId().equals(taskId.value())) {
            throw stale("重规划输入绑定了不同 Task");
        }
        var fact = input.getInput();
        if (!fact.userId().equals(userId) || fact.kind() != ExecutionInput.Kind.MODIFY) {
            throw stale("重规划输入身份或类型不匹配");
        }
        return input;
    }

    private void consumeInput(TaskInputEntity input, Instant at) {
        input.setConsumedAt(at);
        inputs.saveAndFlush(input);
    }

    private static String requireAmendment(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("调整要求不能为空白");
        }
        var normalized = reason.trim();
        if (normalized.length() > 2000) {
            throw new IllegalArgumentException("调整要求不能超过 2000 字符");
        }
        return normalized;
    }

    private void appendEventsAndOutbox(
            List<ExecutionEvent> events, long fencingToken, TaskId outboxTaskId) {
        for (var event : events) {
            var stored = eventWriter.append(event, fencingToken).storedEvent().event();
            var outboxId = "transition-" + stored.eventId().value();
            var existing = transitionOutbox.findByOutboxId(outboxId).orElse(null);
            if (existing != null) {
                if (!existing.getEvent().equals(stored)) {
                    throw stale("outboxId 已绑定不同 cancel event");
                }
                continue;
            }
            var entity = new TaskTransitionOutboxEntity();
            entity.setOutboxId(outboxId);
            entity.setTenantId(stored.tenantId().value());
            entity.setTaskId(outboxTaskId.value());
            entity.setEventId(stored.eventId().value());
            entity.setStatus("PENDING");
            entity.setEvent(stored);
            entity.setCreatedAt(stored.createdAt());
            transitionOutbox.save(entity);
        }
        transitionOutbox.flush();
    }

    private static ExecutionEvent taskAmendedEvent(
            Task task,
            Execution execution,
            UserId userId,
            String scope,
            String reason,
            String inputId,
            Instant at) {
        var producerKey = "task-amended:" + inputId;
        return new ExecutionEvent(
                CanonicalExecutionEventId.of(task.tenantId(), execution.executionId(), producerKey),
                task.tenantId(),
                task.conversationId(),
                execution.sessionId(),
                task.taskId(),
                execution.executionId(),
                execution.runId(),
                execution.parentExecutionId(),
                1,
                ExecutionEventType.TASK_STATUS_CHANGED,
                ExecutionEventStatus.RUNNING,
                task.controlMode(),
                OwnerType.HUMAN,
                null,
                null,
                userId,
                execution.correlationId(),
                null,
                null,
                new ExecutionEventPayload(
                        Map.of(
                                "taskStatus", task.status().name(),
                                "amendmentScope", scope,
                                "reason", reason)),
                at,
                null);
    }

    private static ExecutionEvent promotionEvent(
            Task task, Execution origin, AssistantCommand rootCommand, Instant at) {
        return new ExecutionEvent(
                CanonicalExecutionEventId.of(
                        origin.tenantId(), origin.executionId(), "execution-promoted"),
                origin.tenantId(),
                origin.conversationId(),
                origin.sessionId(),
                null,
                origin.executionId(),
                origin.runId(),
                null,
                1,
                ExecutionEventType.EXECUTION_PROMOTED,
                ExecutionEventStatus.COMPLETED,
                rootCommand.controlMode(),
                OwnerType.ASSISTANT,
                rootCommand.assistantId(),
                null,
                null,
                origin.correlationId(),
                null,
                rootCommand.idempotencyKey(),
                new ExecutionEventPayload(
                        Map.of(
                                "promotedTaskId", task.taskId().value(),
                                "taskStatus", task.status().name(),
                                "executionStatus", Execution.Status.PROMOTED.name())),
                at,
                null);
    }

    private static ExecutionEvent executionCanceledEvent(
            Task task, Execution execution, UserId userId, String reason, Instant at) {
        return lifecycleEvent(
                task,
                execution,
                userId,
                ExecutionEventType.EXECUTION_CANCELED,
                Map.of(
                        "taskStatus",
                        Task.Status.CANCELED.name(),
                        "executionStatus",
                        Execution.Status.CANCELED.name(),
                        "cancellationPhase",
                        "COMPLETED",
                        "reason",
                        reason),
                "execution-canceled",
                at);
    }

    private static ExecutionEvent taskPauseEvent(
            Task task,
            Execution execution,
            UserId userId,
            String reason,
            String phase,
            String requestId,
            Instant at) {
        return new ExecutionEvent(
                CanonicalExecutionEventId.of(
                        task.tenantId(),
                        execution.executionId(),
                        ("REQUESTED".equals(phase) ? "task-pause-requested:" : "task-paused:")
                                + requestId),
                task.tenantId(),
                task.conversationId(),
                execution.sessionId(),
                task.taskId(),
                execution.executionId(),
                execution.runId(),
                execution.parentExecutionId(),
                1,
                ExecutionEventType.TASK_STATUS_CHANGED,
                ExecutionEventStatus.PAUSED,
                task.controlMode(),
                OwnerType.HUMAN,
                null,
                null,
                userId,
                execution.correlationId(),
                null,
                null,
                new ExecutionEventPayload(
                        Map.of(
                                "taskStatus", task.status().name(),
                                "pausePhase", phase,
                                "pauseRequestId", requestId,
                                "reason", reason)),
                at,
                null);
    }

    private static ExecutionEvent ownershipTransferredEvent(
            Task task,
            Execution execution,
            UserId userId,
            Task.Owner previousOwner,
            Task.Owner nextOwner,
            String reason,
            boolean handBackFreshAttempt,
            String producer,
            Instant at) {
        return new ExecutionEvent(
                CanonicalExecutionEventId.of(task.tenantId(), execution.executionId(), producer),
                task.tenantId(),
                task.conversationId(),
                execution.sessionId(),
                task.taskId(),
                execution.executionId(),
                execution.runId(),
                execution.parentExecutionId(),
                1,
                ExecutionEventType.OWNERSHIP_TRANSFERRED,
                handBackFreshAttempt ? ExecutionEventStatus.RUNNING : ExecutionEventStatus.PAUSED,
                task.controlMode(),
                OwnerType.HUMAN,
                null,
                null,
                userId,
                execution.correlationId(),
                null,
                null,
                new ExecutionEventPayload(
                        Map.of(
                                "taskStatus", task.status().name(),
                                "previousOwnerKind", previousOwner.kind().name(),
                                "nextOwnerKind", nextOwner.kind().name(),
                                "reason", reason,
                                "handBackFreshAttempt", handBackFreshAttempt)),
                at,
                null);
    }

    private static ExecutionEvent taskCancellationEvent(
            Task task,
            Execution execution,
            UserId userId,
            String reason,
            String phase,
            Instant at) {
        return lifecycleEvent(
                task,
                execution,
                userId,
                ExecutionEventType.TASK_STATUS_CHANGED,
                Map.of(
                        "taskStatus", task.status().name(),
                        "cancellationPhase", phase,
                        "reason", reason),
                "REQUESTED".equals(phase) ? "task-cancel-requested" : "task-canceled",
                at);
    }

    private static ExecutionEvent lifecycleEvent(
            Task task,
            Execution execution,
            UserId userId,
            ExecutionEventType type,
            Map<String, Object> payload,
            String producer,
            Instant at) {
        var producerKey = producer + ':' + task.taskId().value();
        return new ExecutionEvent(
                CanonicalExecutionEventId.of(task.tenantId(), execution.executionId(), producerKey),
                task.tenantId(),
                task.conversationId(),
                execution.sessionId(),
                task.taskId(),
                execution.executionId(),
                execution.runId(),
                execution.parentExecutionId(),
                1,
                type,
                ExecutionEventStatus.CANCELED,
                task.controlMode(),
                OwnerType.HUMAN,
                null,
                null,
                userId,
                execution.correlationId(),
                null,
                null,
                new ExecutionEventPayload(payload),
                at,
                null);
    }

    @Override
    @Transactional
    public Optional<ClaimedNode> claim(
            TenantId tenantId,
            String dispatchId,
            String workerId,
            Duration leaseTtl,
            Lease conversationLease,
            Instant at) {
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        if (dispatchId == null || dispatchId.isBlank() || workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("dispatchId/workerId 不能为空白");
        }
        if (leaseTtl == null || leaseTtl.isZero() || leaseTtl.isNegative()) {
            throw new IllegalArgumentException("leaseTtl 必须为正数");
        }
        var dispatchEntity = dispatches.claimPending(tenantId.value(), dispatchId, at).orElse(null);
        if (dispatchEntity == null) {
            return Optional.empty();
        }
        var dispatch = dispatchEntity.getDispatch();
        var executionEntity =
                executions
                        .findForUpdate(tenantId.value(), dispatch.executionId().value())
                        .orElseThrow(() -> stale("dispatch 缺少 Execution"));
        var execution = executionEntity.getExecution();
        if (execution.scope() != Execution.Scope.TASK_NODE || execution.taskId() == null) {
            throw stale("node dispatch 只能指向 TASK_NODE Execution");
        }
        var taskEntity =
                tasks.findForUpdate(tenantId.value(), execution.taskId().value())
                        .orElseThrow(() -> stale("Execution 缺少 Task"));
        var task = taskEntity.getTask();
        if (task.status() != Task.Status.READY
                && task.status() != Task.Status.RUNNING
                && task.status() != Task.Status.VERIFYING) {
            return Optional.empty();
        }
        if (!Objects.equals(task.currentPlanId(), execution.planId())
                || !Objects.equals(task.currentPlanRevision(), execution.planRevision())) {
            throw stale("Execution 不属于 Task current plan");
        }
        var planEntity =
                plans.findCurrentForUpdate(
                                tenantId.value(),
                                task.taskId().value(),
                                execution.planId(),
                                execution.planRevision())
                        .orElseThrow(() -> stale("current plan 不存在"));
        if (!TaskPlan.PlanStatus.FROZEN.name().equals(planEntity.getStatus())) {
            throw stale("非 FROZEN plan 不得 claim");
        }
        var nodeEntity =
                nodes.findNodeForUpdate(
                                tenantId.value(),
                                task.taskId().value(),
                                execution.planId(),
                                execution.planRevision(),
                                execution.nodeId())
                        .orElseThrow(() -> stale("Execution 对应 TaskNode 不存在"));
        if (!Objects.equals(nodeEntity.getCurrentExecutionId(), execution.executionId().value())
                || !Objects.equals(nodeEntity.getCurrentAttempt(), execution.attemptNo())
                || !TaskPlan.Status.READY.name().equals(nodeEntity.getStatus())) {
            throw stale("TaskNode current execution/attempt/status 已变化");
        }
        if (task.status() == Task.Status.VERIFYING
                && nodeEntity.getDefinition().kind() != TaskPlan.TaskNode.Kind.AGGREGATOR) {
            throw stale("VERIFYING Task 只允许 finalizer claim");
        }
        var active =
                nodes.countClaimedOrRunning(
                        tenantId.value(),
                        task.taskId().value(),
                        execution.planId(),
                        execution.planRevision());
        if (active >= planEntity.getMaxParallelism()) {
            return Optional.empty();
        }
        var claimed =
                new TaskDispatch(
                        dispatch.dispatchId(),
                        dispatch.tenantId(),
                        dispatch.executionId(),
                        TaskDispatch.Status.CLAIMED,
                        dispatch.nextRunAt(),
                        workerId,
                        at.plus(leaseTtl),
                        dispatch.generation() + 1,
                        dispatches.nextFence(),
                        dispatch.deliveryAttempts() + 1,
                        dispatch.lastError(),
                        dispatch.version(),
                        dispatch.createdAt(),
                        at);
        dispatchEntity.setDispatch(claimed);
        nodeEntity.setStatus(TaskPlan.Status.RUNNING.name());
        var running = execution.withStatus(Execution.Status.RUNNING, at);
        executionEntity.setExecution(running);
        var command =
                executionEntity
                        .getCommand()
                        .withDispatch(
                                conversationLease,
                                claimed.dispatchId(),
                                claimed.generation(),
                                claimed.fencingToken(),
                                claimed.leaseOwner(),
                                claimed.leaseUntil(),
                                at);
        executionEntity.setCommand(command);
        var claimedTaskStatus =
                nodeEntity.getDefinition().kind() == TaskPlan.TaskNode.Kind.AGGREGATOR
                        ? Task.Status.VERIFYING
                        : Task.Status.RUNNING;
        var runningTask =
                task.status() == claimedTaskStatus
                        ? task
                        : task.withRuntime(
                                claimedTaskStatus,
                                task.owner(),
                                task.budgetUsage(),
                                task.checkpoint(),
                                task.recoveryPoint(),
                                at);
        taskEntity.setTask(runningTask);
        dispatches.save(dispatchEntity);
        nodes.save(nodeEntity);
        tasks.save(taskEntity);
        executions.saveAndFlush(executionEntity);
        return Optional.of(
                new ClaimedNode(
                        runningTask,
                        assemblePlan(tenantId, planEntity),
                        nodeEntity.toDomain(predecessors(tenantId, execution)),
                        running,
                        claimed,
                        command,
                        value(taskEntity.getRuntimeLockVersion()),
                        value(planEntity.getRuntimeLockVersion()),
                        value(nodeEntity.getRuntimeLockVersion()),
                        value(executionEntity.getRuntimeLockVersion())));
    }

    @Override
    @Transactional
    public CommitResult commit(NodeResultCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        var taskEntity =
                tasks.findForUpdate(command.tenantId().value(), command.taskId().value())
                        .orElseThrow(() -> stale("Task 不存在"));
        var planEntity =
                plans.findCurrentForUpdate(
                                command.tenantId().value(),
                                command.taskId().value(),
                                command.planId(),
                                command.planRevision())
                        .orElseThrow(() -> stale("current plan 不存在"));
        var nodeEntity =
                nodes.findNodeForUpdate(
                                command.tenantId().value(),
                                command.taskId().value(),
                                command.planId(),
                                command.planRevision(),
                                command.nodeId())
                        .orElseThrow(() -> stale("TaskNode 不存在"));
        var executionEntity =
                executions
                        .findForUpdate(command.tenantId().value(), command.executionId())
                        .orElseThrow(() -> stale("Execution 不存在"));
        var dispatchEntity =
                dispatches
                        .findActiveForUpdate(command.tenantId().value(), command.executionId())
                        .orElse(null);
        var rejection =
                rejection(
                        command,
                        taskEntity,
                        planEntity,
                        nodeEntity,
                        executionEntity,
                        dispatchEntity);
        if (rejection != null) {
            return new CommitResult(
                    false,
                    taskEntity.getTask(),
                    nodeEntity.toDomain(
                            predecessors(command.tenantId(), executionEntity.getExecution())),
                    List.of(),
                    rejection);
        }
        var dispatch = dispatchEntity.getDispatch();
        var failed = command.failure() != null && !command.failure().isBlank();
        nodeEntity.setStatus(
                failed ? TaskPlan.Status.FAILED.name() : TaskPlan.Status.COMPLETED.name());
        nodeEntity.setResult(failed ? null : Objects.requireNonNullElse(command.result(), ""));
        nodeEntity.setFailure(failed ? command.failure() : null);
        executionEntity.setExecution(
                executionEntity
                        .getExecution()
                        .withStatus(
                                failed ? Execution.Status.FAILED : Execution.Status.COMPLETED,
                                command.at()));
        dispatchEntity.setDispatch(
                new TaskDispatch(
                        dispatch.dispatchId(),
                        dispatch.tenantId(),
                        dispatch.executionId(),
                        failed ? TaskDispatch.Status.CANCELED : TaskDispatch.Status.DONE,
                        dispatch.nextRunAt(),
                        null,
                        null,
                        dispatch.generation(),
                        dispatch.fencingToken(),
                        dispatch.deliveryAttempts(),
                        failed ? command.failure() : dispatch.lastError(),
                        dispatch.version(),
                        dispatch.createdAt(),
                        command.at()));
        nodes.save(nodeEntity);
        executions.save(executionEntity);
        dispatches.saveAndFlush(dispatchEntity);
        var updatedPlan = assemblePlan(command.tenantId(), planEntity);
        var currentTask = taskEntity.getTask();
        var finalizer = nodeEntity.getDefinition().kind() == TaskPlan.TaskNode.Kind.AGGREGATOR;
        final Task updatedTask;
        final boolean planCompletedWithoutFinalizer;
        if (!failed && finalizer) {
            if (!updatedPlan.completed()) {
                throw stale("finalizer 完成时 TaskPlan 尚未满足完成条件");
            }
            if (currentTask.status() != Task.Status.VERIFYING
                    || currentTask.owner().kind() != Task.OwnerKind.ASSISTANT
                    || !currentTask
                            .owner()
                            .ownerId()
                            .equals(executionEntity.getCommand().assistantId().value())) {
                throw stale("finalizer 不是 current Task Owner Assistant 的 VERIFYING execution");
            }
            var rootResult =
                    new Task.RootResult(
                            Objects.requireNonNullElse(updatedPlan.aggregateResults(), ""),
                            Objects.requireNonNullElse(command.result(), ""),
                            currentTask.owner().ownerId(),
                            command.planId(),
                            command.planRevision(),
                            command.nodeId(),
                            executionEntity.getExecution().executionId(),
                            command.attemptNo(),
                            command.dispatchId(),
                            command.generation(),
                            command.fencingToken(),
                            command.at());
            updatedTask = currentTask.completeWithRootResult(rootResult, command.at());
            planCompletedWithoutFinalizer = false;
        } else {
            var finalizerReady =
                    !failed
                            && new com.xuejiai.aaf.framework.intelligent.assistant.model
                                            .TaskDagService()
                                    .ready(updatedPlan).stream()
                                            .anyMatch(
                                                    node ->
                                                            node.kind()
                                                                    == TaskPlan.TaskNode.Kind
                                                                            .AGGREGATOR);
            planCompletedWithoutFinalizer = !failed && updatedPlan.completed();
            var nextTaskStatus =
                    failed
                            ? (updatedPlan.failurePolicy() == TaskPlan.FailurePolicy.FAIL_TASK
                                    ? Task.Status.FAILED
                                    : Task.Status.PAUSED)
                            : (planCompletedWithoutFinalizer
                                    ? Task.Status.PAUSED
                                    : (finalizerReady
                                            ? Task.Status.VERIFYING
                                            : Task.Status.RUNNING));
            var checkpoint =
                    failed
                            ? currentTask
                                    .checkpoint()
                                    .withAnnotation(
                                            "nodeFailure",
                                            command.nodeId() + ":" + command.failure())
                            : (planCompletedWithoutFinalizer
                                    ? currentTask
                                            .checkpoint()
                                            .withAnnotation(
                                                    "finalizerMissing",
                                                    updatedPlan.planId()
                                                            + ":"
                                                            + updatedPlan.revision())
                                    : currentTask.checkpoint());
            updatedTask =
                    currentTask.withRuntime(
                            nextTaskStatus,
                            currentTask.owner(),
                            currentTask.budgetUsage(),
                            checkpoint,
                            currentTask.recoveryPoint(),
                            command.at());
        }
        taskEntity.setTask(updatedTask);
        tasks.saveAndFlush(taskEntity);
        var released =
                failed || planCompletedWithoutFinalizer || updatedTask.terminal()
                        ? List.<MaterializedDispatch>of()
                        : materializeLocked(
                                command.tenantId(),
                                command.taskId(),
                                executionEntity.getCommand(),
                                command.at());
        return new CommitResult(
                true,
                updatedTask,
                nodeEntity.toDomain(
                        predecessors(command.tenantId(), executionEntity.getExecution())),
                released,
                null);
    }

    @Override
    @Transactional
    public int recoverExpired(Instant at) {
        var recovered = 0;
        for (var entity :
                dispatches.findExpiredLeasesByExecutionScope(
                        Execution.Scope.TASK_NODE.name(), at)) {
            var current = entity.getDispatch();
            var executionEntity =
                    executions
                            .findForUpdate(
                                    current.tenantId().value(), current.executionId().value())
                            .orElseThrow(() -> stale("过期 dispatch 缺少 Execution"));
            var execution = executionEntity.getExecution();
            if (execution.scope() != Execution.Scope.TASK_NODE || execution.taskId() == null) {
                entity.setDispatch(
                        terminalDispatch(
                                current,
                                TaskDispatch.Status.CANCELED,
                                "expired dispatch identity is not TASK_NODE",
                                at));
                dispatches.save(entity);
                continue;
            }
            var nodeEntity =
                    nodes.findNodeForUpdate(
                                    current.tenantId().value(),
                                    execution.taskId().value(),
                                    execution.planId(),
                                    execution.planRevision(),
                                    execution.nodeId())
                            .orElseThrow(() -> stale("过期 dispatch 缺少 TaskNode"));
            if (!Objects.equals(nodeEntity.getCurrentExecutionId(), execution.executionId().value())
                    || !Objects.equals(nodeEntity.getCurrentAttempt(), execution.attemptNo())
                    || !TaskPlan.Status.RUNNING.name().equals(nodeEntity.getStatus())) {
                entity.setDispatch(
                        terminalDispatch(
                                current,
                                TaskDispatch.Status.CANCELED,
                                "expired dispatch no longer owns current node",
                                at));
                dispatches.save(entity);
                continue;
            }
            entity.setDispatch(
                    new TaskDispatch(
                            current.dispatchId(),
                            current.tenantId(),
                            current.executionId(),
                            TaskDispatch.Status.PENDING,
                            at,
                            null,
                            null,
                            current.generation() + 1,
                            dispatches.nextFence(),
                            current.deliveryAttempts(),
                            "lease expired",
                            current.version(),
                            current.createdAt(),
                            at));
            nodeEntity.setStatus(TaskPlan.Status.READY.name());
            executionEntity.setExecution(execution.withStatus(Execution.Status.DISPATCHED, at));
            dispatches.save(entity);
            nodes.save(nodeEntity);
            executions.save(executionEntity);
            recovered++;
        }
        executions.flush();
        nodes.flush();
        dispatches.flush();
        return recovered;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MaterializedDispatch> findDue(Instant at, int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit 必须在 1..100");
        }
        var result = new ArrayList<MaterializedDispatch>();
        for (var dispatch :
                dispatches.findDispatchableByExecutionScope(
                        Execution.Scope.TASK_NODE.name(), at, PageRequest.of(0, limit))) {
            var execution =
                    executions
                            .findByTenantIdAndExecutionId(
                                    dispatch.getTenantId(), dispatch.getExecutionId())
                            .map(TaskExecutionEntity::getExecution)
                            .orElseThrow(() -> stale("due dispatch 缺少 Execution"));
            result.add(
                    new MaterializedDispatch(
                            new TenantId(dispatch.getTenantId()),
                            execution.taskId(),
                            dispatch.getDispatchId()));
        }
        return List.copyOf(result);
    }

    private List<MaterializedDispatch> materializeLocked(
            TenantId tenantId, TaskId taskId, AssistantCommand sourceCommand, Instant at) {
        var taskEntity =
                tasks.findForUpdate(tenantId.value(), taskId.value())
                        .orElseThrow(
                                () -> new IllegalArgumentException("Task 不存在: " + taskId.value()));
        var task = taskEntity.getTask();
        if ((task.status() != Task.Status.READY
                        && task.status() != Task.Status.RUNNING
                        && task.status() != Task.Status.VERIFYING)
                || task.currentPlanId() == null) {
            return List.of();
        }
        var planEntity =
                plans.findCurrentForUpdate(
                                tenantId.value(),
                                taskId.value(),
                                task.currentPlanId(),
                                task.currentPlanRevision())
                        .orElseThrow(() -> stale("Task current plan 不存在"));
        if (!TaskPlan.PlanStatus.FROZEN.name().equals(planEntity.getStatus())) {
            return List.of();
        }
        var nodeEntities =
                nodes.findPlanNodesForUpdate(
                        tenantId.value(),
                        taskId.value(),
                        task.currentPlanId(),
                        task.currentPlanRevision());
        var plan = assemblePlan(tenantId, planEntity, nodeEntities);
        var candidates =
                new com.xuejiai.aaf.framework.intelligent.assistant.model.TaskDagService()
                        .ready(plan);
        if (task.status() == Task.Status.VERIFYING) {
            candidates =
                    candidates.stream()
                            .filter(node -> node.kind() == TaskPlan.TaskNode.Kind.AGGREGATOR)
                            .toList();
        }
        var created = new ArrayList<MaterializedDispatch>();
        for (var candidate : candidates) {
            var entity =
                    nodeEntities.stream()
                            .filter(node -> node.getNodeId().equals(candidate.nodeId()))
                            .findFirst()
                            .orElseThrow();
            var currentExecutionId = entity.getCurrentExecutionId();
            if (currentExecutionId != null
                    && dispatches
                            .findActiveForUpdate(tenantId.value(), currentExecutionId)
                            .isPresent()) {
                continue;
            }
            var requestedSameAttempt =
                    candidate.status() == TaskPlan.Status.READY && entity.getCurrentAttempt() > 0;
            var sameAttemptResume = false;
            var policyFreshAttempt = false;
            if (requestedSameAttempt) {
                var currentExecution =
                        executions
                                .findForUpdate(
                                        tenantId.value(),
                                        Objects.requireNonNull(currentExecutionId))
                                .orElseThrow(() -> stale("READY node 缺少 current Execution"))
                                .getExecution();
                var explicitPauseResume =
                        "SAME_ATTEMPT"
                                .equals(
                                        Objects.toString(
                                                task.checkpoint()
                                                        .annotations()
                                                        .get(PAUSE_RESUME_MODE),
                                                ""));
                var ack = pauseAcks(task).get(currentExecutionId);
                var stateAvailable = !explicitPauseResume || pauseAckSaved(ack);
                var stateCompatible =
                        !explicitPauseResume
                                || (ack != null
                                        && currentExecution
                                                .stateSlotId()
                                                .equals(
                                                        Objects.toString(
                                                                ack.get("stateSlotId"), ""))
                                        && AGENT_STATE_SCHEMA.equals(
                                                Objects.toString(ack.get("stateSchema"), "")));
                var decision =
                        new ExecutionResumePolicy()
                                .decide(
                                        new ExecutionResumePolicy.Request(
                                                currentExecution,
                                                currentExecution
                                                        .ownerSnapshot()
                                                        .equals(task.owner()),
                                                true,
                                                stateAvailable,
                                                stateCompatible));
                sameAttemptResume = decision == ExecutionResumePolicy.Decision.SAME_ATTEMPT;
                policyFreshAttempt = decision == ExecutionResumePolicy.Decision.FRESH_ATTEMPT;
                if (decision == ExecutionResumePolicy.Decision.REJECT) {
                    throw stale("current Execution 状态不允许恢复");
                }
            }
            var freshAttempt =
                    candidate.status() == TaskPlan.Status.RETRYABLE || policyFreshAttempt;
            var attempt =
                    freshAttempt
                            ? entity.getCurrentAttempt() + 1
                            : (sameAttemptResume ? entity.getCurrentAttempt() : 1);
            var executionId =
                    sameAttemptResume
                            ? new ExecutionId(Objects.requireNonNull(currentExecutionId))
                            : new ExecutionId("execution:" + UUID.randomUUID());
            var sessionId =
                    sameAttemptResume
                            ? new SessionId(Objects.requireNonNull(entity.getCurrentSessionId()))
                            : new SessionId("session:" + UUID.randomUUID());
            var materializedNode =
                    copyNode(
                            candidate,
                            TaskPlan.Status.READY,
                            attempt,
                            executionId,
                            sessionId,
                            null,
                            null);
            var effectivePlan = replaceNode(plan, materializedNode);
            var input = effectivePlan.resolveInput(materializedNode);

            TaskExecutionEntity executionEntity;
            Execution execution;
            AssistantCommand childCommand;
            long dispatchGeneration;
            if (sameAttemptResume) {
                executionEntity =
                        executions
                                .findForUpdate(tenantId.value(), executionId.value())
                                .orElseThrow(() -> stale("READY node 缺少 current Execution"));
                execution =
                        executionEntity.getExecution().withStatus(Execution.Status.DISPATCHED, at);
                childCommand =
                        executionEntity
                                .getCommand()
                                .withoutDispatch(null, at)
                                .asResume(at)
                                .withInput(input, null, at);
                dispatchGeneration =
                        dispatches
                                .findFirstByTenantIdAndExecutionIdOrderByIdDesc(
                                        tenantId.value(), executionId.value())
                                .map(previous -> previous.getDispatch().generation() + 1)
                                .orElse(1L);
            } else {
                var commandSource = sourceCommand;
                ExecutionId predecessor = null;
                if (freshAttempt) {
                    var previous =
                            executions
                                    .findForUpdate(tenantId.value(), currentExecutionId)
                                    .orElseThrow(
                                            () -> stale("RETRYABLE node 缺少 predecessor Execution"));
                    predecessor = previous.getExecution().executionId();
                    commandSource = previous.getCommand().withoutDispatch(null, at);
                    previous.setExecution(
                            previous.getExecution().withStatus(Execution.Status.SUPERSEDED, at));
                    executions.save(previous);
                }
                childCommand =
                        commandSource.forNode(
                                materializedNode,
                                input,
                                null,
                                at,
                                plan.goal().aggregationContract().kind());
                execution =
                        new Execution(
                                tenantId,
                                childCommand.userId(),
                                childCommand.conversationId(),
                                taskId,
                                plan.planId(),
                                plan.revision(),
                                materializedNode.nodeId(),
                                executionId,
                                sessionId,
                                childCommand.runId(),
                                childCommand.correlationId(),
                                childCommand.parentExecutionId(),
                                predecessor,
                                Execution.Scope.TASK_NODE,
                                attempt,
                                executionId.value(),
                                Execution.Status.DISPATCHED,
                                Execution.PromotionState.INELIGIBLE,
                                0,
                                task.owner(),
                                freshAttempt ? 1 : 0,
                                at,
                                at);
                executionEntity = new TaskExecutionEntity();
                dispatchGeneration = 1;
            }
            executionEntity.setExecution(execution);
            executionEntity.setCommand(childCommand);
            executions.save(executionEntity);
            var dispatch =
                    new TaskDispatch(
                            "dispatch:" + UUID.randomUUID(),
                            tenantId,
                            executionId,
                            TaskDispatch.Status.PENDING,
                            at,
                            null,
                            null,
                            dispatchGeneration,
                            dispatches.nextFence(),
                            0,
                            null,
                            0,
                            at,
                            at);
            var dispatchEntity = new TaskDispatchEntity();
            dispatchEntity.setDispatch(dispatch);
            dispatches.save(dispatchEntity);
            entity.setStatus(TaskPlan.Status.READY.name());
            entity.setCurrentExecutionId(executionId.value());
            entity.setCurrentSessionId(sessionId.value());
            entity.setCurrentAttempt(attempt);
            nodes.save(entity);
            created.add(new MaterializedDispatch(tenantId, taskId, dispatch.dispatchId()));
        }
        executions.flush();
        dispatches.flush();
        nodes.flush();
        return List.copyOf(created);
    }

    private TaskPlan assemblePlan(TenantId tenantId, TaskPlanEntity planEntity) {
        return assemblePlan(
                tenantId,
                planEntity,
                nodes.findByTenantIdAndTaskIdAndPlanIdAndPlanRevisionOrderByNodeId(
                        tenantId.value(),
                        planEntity.getTaskId(),
                        planEntity.getPlanId(),
                        planEntity.getPlanRevision()));
    }

    private TaskPlan assemblePlan(
            TenantId tenantId, TaskPlanEntity planEntity, List<TaskNodeEntity> nodeEntities) {
        var predecessorIds = new LinkedHashMap<String, LinkedHashSet<String>>();
        dependencies
                .findByTenantIdAndTaskIdAndPlanIdAndPlanRevisionOrderBySuccessorNodeIdAscPredecessorNodeIdAsc(
                        tenantId.value(),
                        planEntity.getTaskId(),
                        planEntity.getPlanId(),
                        planEntity.getPlanRevision())
                .forEach(
                        edge ->
                                predecessorIds
                                        .computeIfAbsent(
                                                edge.getSuccessorNodeId(),
                                                ignored -> new LinkedHashSet<>())
                                        .add(edge.getPredecessorNodeId()));
        var mapped = new LinkedHashMap<String, TaskPlan.TaskNode>();
        nodeEntities.forEach(
                node ->
                        mapped.put(
                                node.getNodeId(),
                                node.toDomain(
                                        predecessorIds.getOrDefault(
                                                node.getNodeId(), new LinkedHashSet<>()))));
        return new TaskPlan(
                planEntity.getPlanId(),
                new TaskId(planEntity.getTaskId()),
                planEntity.getPlanRevision(),
                TaskPlan.PlanStatus.valueOf(planEntity.getStatus()),
                planEntity.getGoal(),
                planEntity.getMaxParallelism(),
                TaskPlan.FailurePolicy.valueOf(planEntity.getFailurePolicy()),
                planEntity.getGraphHash(),
                value(planEntity.getRuntimeLockVersion()),
                mapped);
    }

    private java.util.Set<String> predecessors(TenantId tenantId, Execution execution) {
        var result = new LinkedHashSet<String>();
        dependencies
                .findByTenantIdAndTaskIdAndPlanIdAndPlanRevisionOrderBySuccessorNodeIdAscPredecessorNodeIdAsc(
                        tenantId.value(),
                        execution.taskId().value(),
                        execution.planId(),
                        execution.planRevision())
                .stream()
                .filter(edge -> edge.getSuccessorNodeId().equals(execution.nodeId()))
                .forEach(edge -> result.add(edge.getPredecessorNodeId()));
        return java.util.Set.copyOf(result);
    }

    private static String rejection(
            NodeResultCommand command,
            TaskRootEntity task,
            TaskPlanEntity plan,
            TaskNodeEntity node,
            TaskExecutionEntity execution,
            TaskDispatchEntity dispatch) {
        var currentTask = task.getTask();
        if (currentTask.rootResult() != null) {
            return "root result already committed";
        }
        if (!Objects.equals(currentTask.currentPlanId(), command.planId())
                || !Objects.equals(currentTask.currentPlanRevision(), command.planRevision())
                || currentTask.terminal()
                || (currentTask.status() != Task.Status.RUNNING
                        && currentTask.status() != Task.Status.VERIFYING)) {
            return "task current plan/status changed";
        }
        if (!TaskPlan.PlanStatus.FROZEN.name().equals(plan.getStatus())) {
            return "current plan is not frozen";
        }
        if (value(task.getRuntimeLockVersion()) != command.expectedTaskVersion()
                || value(plan.getRuntimeLockVersion()) != command.expectedPlanVersion()
                || value(node.getRuntimeLockVersion()) != command.expectedNodeVersion()
                || value(execution.getRuntimeLockVersion()) != command.expectedExecutionVersion()) {
            return "aggregate version changed";
        }
        if (dispatch == null) {
            return "active dispatch missing";
        }
        var current = dispatch.getDispatch();
        if (!Objects.equals(node.getCurrentExecutionId(), command.executionId())
                || !Objects.equals(node.getCurrentAttempt(), command.attemptNo())
                || !current.accepts(
                        command.dispatchId(),
                        command.generation(),
                        command.fencingToken(),
                        command.leaseOwner(),
                        command.at())) {
            return "stale execution/attempt/dispatch generation/fence";
        }
        return null;
    }

    private static TaskPlan replaceNode(TaskPlan source, TaskPlan.TaskNode replacement) {
        var mapped = new LinkedHashMap<>(source.nodes());
        mapped.put(replacement.nodeId(), replacement);
        return new TaskPlan(
                source.planId(),
                source.taskId(),
                source.revision(),
                source.planStatus(),
                source.goal(),
                source.maxParallelism(),
                source.failurePolicy(),
                null,
                source.version(),
                mapped);
    }

    private static TaskPlan.TaskNode copyNode(
            TaskPlan.TaskNode source,
            TaskPlan.Status status,
            int attempt,
            ExecutionId executionId,
            SessionId sessionId,
            String result,
            String failure) {
        return new TaskPlan.TaskNode(
                source.nodeId(),
                source.kind(),
                source.description(),
                source.dependsOn(),
                source.inputBindings(),
                source.roleKey(),
                source.skillKey(),
                source.assistantTarget(),
                source.modelSelection(),
                status,
                source.retryable(),
                attempt,
                source.maxAttempts(),
                executionId,
                sessionId,
                result,
                failure,
                source.clarifiedParameters());
    }

    private static TaskDispatch terminalDispatch(
            TaskDispatch source, TaskDispatch.Status status, String error, Instant at) {
        return new TaskDispatch(
                source.dispatchId(),
                source.tenantId(),
                source.executionId(),
                status,
                source.nextRunAt(),
                null,
                null,
                source.generation(),
                source.fencingToken(),
                source.deliveryAttempts(),
                error,
                source.version(),
                source.createdAt(),
                at);
    }

    private static long value(Long value) {
        return value == null ? 0 : value;
    }

    private static IllegalStateException stale(String message) {
        return new IllegalStateException("stale node dispatch: " + message);
    }
}
