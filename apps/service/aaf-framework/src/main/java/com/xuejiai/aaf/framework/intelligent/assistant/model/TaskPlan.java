package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskPlan.TaskNode.Kind;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskPlanDraft.ExecutorAssignment;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;

/** Task 聚合内不可变版本化 DAG；节点运行状态按 Node version 独立推进。 */
public record TaskPlan(
        String planId,
        TaskId taskId,
        int revision,
        PlanStatus planStatus,
        Goal goal,
        int maxParallelism,
        FailurePolicy failurePolicy,
        String graphHash,
        long version,
        Map<String, TaskNode> nodes) {

    public TaskPlan(TaskId taskId, Goal goal, int maxParallelism, Map<String, TaskNode> nodes) {
        this(
                "plan:" + taskId.value(),
                taskId,
                1,
                PlanStatus.FROZEN,
                goal,
                maxParallelism,
                FailurePolicy.PAUSE_TASK,
                graphHash(nodes),
                0,
                nodes);
    }

    public TaskPlan {
        if (planId == null || planId.isBlank()) throw new IllegalArgumentException("planId 不能为空白");
        Objects.requireNonNull(taskId, "taskId 不能为空");
        if (revision < 1) throw new IllegalArgumentException("revision 必须大于 0");
        Objects.requireNonNull(planStatus, "planStatus 不能为空");
        Objects.requireNonNull(goal, "goal 不能为空");
        if (maxParallelism < 1) throw new IllegalArgumentException("maxParallelism 必须大于 0");
        Objects.requireNonNull(failurePolicy, "failurePolicy 不能为空");
        if (version < 0) throw new IllegalArgumentException("version 不能为负数");
        var frozenTaskNodes = Map.copyOf(Objects.requireNonNull(nodes, "nodes 不能为空"));
        nodes = frozenTaskNodes;
        if (frozenTaskNodes.isEmpty()) throw new IllegalArgumentException("TaskPlan 至少包含一个节点");
        var computedGraphHash = graphHash(frozenTaskNodes);
        if (graphHash == null || graphHash.isBlank()) graphHash = computedGraphHash;
        if (!computedGraphHash.equals(graphHash))
            throw new IllegalArgumentException("graphHash 与 DAG 不一致");
        validateReferences(frozenTaskNodes);
        goal.completionEvidence()
                .forEach(
                        evidence -> {
                            if (!frozenTaskNodes.containsKey(evidence)) {
                                throw new IllegalArgumentException(
                                        "Goal 完成证据引用不存在的节点: " + evidence);
                            }
                        });
        var finalizers =
                frozenTaskNodes.values().stream()
                        .filter(node -> node.kind() == Kind.AGGREGATOR)
                        .toList();
        if (finalizers.size() > 1) {
            throw new IllegalArgumentException("TaskPlan 只能包含一个 AGGREGATOR finalizer");
        }
        if (!finalizers.isEmpty()
                && (!"aggregator".equals(finalizers.getFirst().nodeId())
                        || !goal.completionEvidence().equals(Set.of("aggregator")))) {
            throw new IllegalArgumentException("AGGREGATOR 必须使用保留键并作为唯一完成证据");
        }
        if (goal.iteration() != null) goal.iteration().validate(frozenTaskNodes);
        rejectCycles(frozenTaskNodes);
        var running =
                frozenTaskNodes.values().stream()
                        .filter(task -> task.status() == Status.RUNNING)
                        .count();
        if (running > maxParallelism) throw new IllegalArgumentException("运行中节点超过并行度限制");
    }

    public List<TaskDependency> dependencies() {
        return nodes.values().stream()
                .flatMap(
                        node ->
                                node.dependsOn().stream()
                                        .map(
                                                predecessor ->
                                                        new TaskDependency(
                                                                taskId,
                                                                planId,
                                                                revision,
                                                                predecessor,
                                                                node.nodeId(),
                                                                TaskDependency.DependencyType
                                                                        .REQUIRED)))
                .sorted(
                        java.util.Comparator.comparing(TaskDependency::predecessorNodeId)
                                .thenComparing(TaskDependency::successorNodeId))
                .toList();
    }

    private static String graphHash(Map<String, TaskNode> nodes) {
        Objects.requireNonNull(nodes, "nodes 不能为空");
        var canonical =
                nodes.values().stream()
                        .sorted(java.util.Comparator.comparing(TaskNode::nodeId))
                        .map(
                                node ->
                                        node.nodeId()
                                                + "<-"
                                                + node.dependsOn().stream().sorted().toList())
                        .reduce((left, right) -> left + "|" + right)
                        .orElse("");
        return UUID.nameUUIDFromBytes(canonical.getBytes(StandardCharsets.UTF_8)).toString();
    }

    public enum PlanStatus {
        DRAFT,
        FROZEN,
        SUPERSEDED
    }

    public enum FailurePolicy {
        FAIL_TASK,
        PAUSE_TASK
    }

    /** 非协调任务的单执行者看板；root 完成后由 Task Owner finalizer 提交唯一根结果。 */
    public static TaskPlan single(TaskId taskId, String description, int maxAttempts) {
        var root =
                TaskNode.pending(
                        "root",
                        Kind.EXECUTOR,
                        description,
                        Set.of(),
                        Map.of(),
                        null,
                        null,
                        TaskModelSelection.auto(),
                        maxAttempts);
        var finalizer =
                TaskNode.pending(
                        "aggregator",
                        Kind.AGGREGATOR,
                        "核验目标与已完成结果；确认完成后逐字输出已冻结结果，不添加说明。",
                        Set.of(root.nodeId()),
                        Map.of("result.root", new TaskPlanDraft.InputBinding(root.nodeId())),
                        null,
                        null,
                        TaskModelSelection.auto(),
                        1);
        return new TaskPlan(
                taskId,
                new Goal(
                        "goal",
                        description,
                        Set.of(finalizer.nodeId()),
                        TaskPlanDraft.AggregationContract.passThrough(root.nodeId()),
                        null),
                1,
                Map.of(root.nodeId(), root, finalizer.nodeId(), finalizer));
    }

    /** 固定 Route 任务：先且仅先运行无业务工具的协调者。 */
    public static TaskPlan coordinated(
            TaskId taskId, String description, String roleKey, String skillKey, int maxAttempts) {
        var coordinator =
                TaskNode.pending(
                        "coordinator",
                        Kind.COORDINATOR,
                        description,
                        Set.of(),
                        Map.of(),
                        roleKey,
                        skillKey,
                        TaskModelSelection.auto(),
                        maxAttempts);
        return new TaskPlan(
                taskId,
                new Goal(
                        "goal",
                        description,
                        Set.of("coordinator"),
                        TaskPlanDraft.AggregationContract.passThrough("coordinator"),
                        null),
                1,
                Map.of(coordinator.nodeId(), coordinator));
    }

    /** Leader 协调的静态 Team；计划只能分配到已冻结 Worker key。 */
    public static TaskPlan teamCoordinated(
            TaskId taskId,
            String description,
            AssistantTarget leader,
            Map<String, AssistantTarget> workers,
            int maxAttempts) {
        Objects.requireNonNull(leader, "leader 不能为空");
        workers = Map.copyOf(Objects.requireNonNull(workers, "workers 不能为空"));
        if (workers.isEmpty() || workers.size() > 8) {
            throw new IllegalArgumentException("Team 必须包含 1..8 个静态 Worker");
        }
        var workerSummary =
                workers.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(
                                entry ->
                                        "%s=%s/%s"
                                                .formatted(
                                                        entry.getKey(),
                                                        entry.getValue().roleKey(),
                                                        entry.getValue().skillKey()))
                        .toList();
        var coordinatorDescription =
                description
                        + "\n\n静态 Team Worker（executors.nodeId/roleKey/skillKey 必须逐项匹配）："
                        + workerSummary;
        var nodes = new LinkedHashMap<String, TaskNode>();
        var coordinator =
                TaskNode.pending(
                        "coordinator",
                        Kind.COORDINATOR,
                        coordinatorDescription,
                        Set.of(),
                        Map.of(),
                        leader.roleKey(),
                        leader.skillKey(),
                        leader,
                        TaskModelSelection.auto(),
                        maxAttempts);
        nodes.put(coordinator.nodeId(), coordinator);
        workers.forEach(
                (memberKey, target) ->
                        nodes.put(
                                memberKey,
                                TaskNode.pending(
                                        memberKey,
                                        Kind.EXECUTOR,
                                        "等待 Leader 分配静态成员任务",
                                        Set.of("coordinator"),
                                        Map.of(),
                                        target.roleKey(),
                                        target.skillKey(),
                                        target,
                                        TaskModelSelection.auto(),
                                        maxAttempts)));
        var workerKeys = workers.keySet().stream().sorted().toList();
        return new TaskPlan(
                taskId,
                new Goal(
                        "goal",
                        description,
                        Set.copyOf(workerKeys),
                        TaskPlanDraft.AggregationContract.orderedConcat(workerKeys, "\n"),
                        null),
                Math.min(workers.size(), 8),
                nodes);
    }

    /**
     * 将已校验计划固化为协调者完成及其执行者 DAG。
     *
     * <p>不再判定新建执行者是否需要先规划（AAF-107 选项 B 架构改造，2026-09-02）——是否规划由每个节点自己在 execution 内决定是否调用 {@code
     * submit_executor_plan}，{@code TaskCommandService} 每轮运行时查询 该节点是否存在活跃 {@code ExecutorPlan}
     * 来分流，不再由建板时的静态标志预先判定。原 {@code planRequirement} 判定函数与 {@code PlanRequirementPolicy} 随本次改造一并废弃。
     */
    public TaskPlan applyTaskPlanDraft(TaskPlanDraft plan) {
        Objects.requireNonNull(plan, "plan 不能为空");
        var coordinator = requireNode("coordinator");
        if (coordinator.kind() != Kind.COORDINATOR || coordinator.status() != Status.RUNNING) {
            throw new IllegalStateException("仅运行中的协调者可以冻结执行计划");
        }
        var teamTargets =
                nodes.values().stream()
                        .filter(task -> task.kind() == Kind.EXECUTOR)
                        .filter(task -> task.assistantTarget() != null)
                        .collect(
                                java.util.stream.Collectors.toUnmodifiableMap(
                                        TaskNode::nodeId, TaskNode::assistantTarget));
        var teamBoard = coordinator.assistantTarget() != null && !teamTargets.isEmpty();
        if (teamBoard) {
            var plannedKeys =
                    plan.executors().stream()
                            .map(ExecutorAssignment::nodeId)
                            .collect(java.util.stream.Collectors.toUnmodifiableSet());
            if (!plannedKeys.equals(teamTargets.keySet())) {
                throw new IllegalArgumentException("Team 协调计划必须且只能覆盖全部静态 Worker");
            }
            // 冻结 roster 为 R 时并行槽位必须恰好等于 R：容量不足只能在领取时排队，
            // 不允许 Leader 在计划里收窄并行度把固定 Team 静默串行化。
            if (plan.maxParallelism() != teamTargets.size()) {
                throw new IllegalArgumentException(
                        "Team 协调计划并行度必须等于冻结 roster: "
                                + plan.maxParallelism()
                                + "/"
                                + teamTargets.size());
            }
        }
        var copy = new LinkedHashMap<String, TaskNode>();
        copy.put(coordinator.nodeId(), coordinator.completed(plan.goal()));
        for (ExecutorAssignment assignment : plan.executors()) {
            if (copy.containsKey(assignment.nodeId())) {
                throw new IllegalArgumentException("协调计划重复子任务标识: " + assignment.nodeId());
            }
            var target = teamBoard ? teamTargets.get(assignment.nodeId()) : null;
            if (target != null
                    && (!target.roleKey().equals(assignment.roleKey())
                            || !target.skillKey().equals(assignment.skillKey()))) {
                throw new IllegalArgumentException("Team 协调计划不能改变 Worker 固定 Role/Skill");
            }
            var kind =
                    plan.iterationGroup() != null
                                    && plan.iterationGroup()
                                            .evaluatorTaskNodeId()
                                            .equals(assignment.nodeId())
                            ? Kind.EVALUATOR
                            : Kind.EXECUTOR;
            copy.put(
                    assignment.nodeId(),
                    TaskNode.pending(
                            assignment.nodeId(),
                            kind,
                            assignment.description(),
                            assignment.dependsOn(),
                            assignment.inputBindings(),
                            assignment.roleKey(),
                            assignment.skillKey(),
                            target,
                            assignment.modelSelection(),
                            assignment.maxAttempts()));
        }
        var aggregatorId = "aggregator";
        if (copy.containsKey(aggregatorId)) {
            throw new IllegalArgumentException("协调计划不能使用保留子任务标识: " + aggregatorId);
        }
        var bindings = new LinkedHashMap<String, TaskPlanDraft.InputBinding>();
        plan.aggregationContract()
                .executorOrder()
                .forEach(
                        executorId ->
                                bindings.put(
                                        "result." + executorId,
                                        new TaskPlanDraft.InputBinding(executorId)));
        var dependencies = new java.util.HashSet<>(plan.aggregationContract().executorOrder());
        if (plan.iterationGroup() != null) {
            dependencies.add(plan.iterationGroup().evaluatorTaskNodeId());
        }
        var finalizerDescription =
                plan.aggregationContract().kind()
                                == TaskPlanDraft.AggregationContract.Kind.AGGREGATOR_REDUCE
                        ? "核验目标与已完成结果，并生成最终聚合输出。"
                        : "核验目标与已完成结果；确认完成后逐字输出已冻结聚合结果，不添加说明。";
        copy.put(
                aggregatorId,
                TaskNode.pending(
                        aggregatorId,
                        Kind.AGGREGATOR,
                        finalizerDescription,
                        Set.copyOf(dependencies),
                        bindings,
                        coordinator.roleKey(),
                        coordinator.skillKey(),
                        coordinator.assistantTarget(),
                        coordinator.modelSelection(),
                        1));
        var iteration =
                plan.iterationGroup() == null
                        ? null
                        : new IterationState(plan.iterationGroup(), 1, null, null, null, null, 0);
        return new TaskPlan(
                taskId,
                new Goal(
                        "goal",
                        plan.goal(),
                        Set.of(aggregatorId),
                        plan.aggregationContract(),
                        iteration),
                plan.maxParallelism(),
                copy);
    }

    public boolean completed() {
        return (goal.iteration() == null || goal.iteration().stopReason() == null)
                && nodes.values().stream().allMatch(task -> task.status() == Status.COMPLETED)
                && goal.completionEvidence().stream()
                        .allMatch(id -> nodes.get(id).status() == Status.COMPLETED);
    }

    public boolean hasTerminalFailure() {
        return nodes.values().stream().anyMatch(task -> task.status() == Status.FAILED);
    }

    public boolean hasRunning() {
        return nodes.values().stream().anyMatch(task -> task.status() == Status.RUNNING);
    }

    public String resolveInput(TaskNode subTask) {
        Objects.requireNonNull(subTask, "subTask 不能为空");
        var resolved = new StringBuilder(subTask.description());
        if (!subTask.inputBindings().isEmpty()) {
            resolved.append("\n\n已冻结输入绑定：");
            subTask.inputBindings().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(
                            entry -> {
                                var source = requireNode(entry.getValue().sourceTaskNodeId());
                                if (source.status() != Status.COMPLETED) {
                                    throw new IllegalStateException(
                                            "输入绑定来源尚未完成: " + source.nodeId());
                                }
                                resolved.append("\n- ")
                                        .append(entry.getKey())
                                        .append(": ")
                                        .append(Objects.requireNonNullElse(source.result(), ""));
                            });
        }
        if (!subTask.clarifiedParameters().isEmpty()) {
            resolved.append("\n\n已补齐参数：");
            subTask.clarifiedParameters().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(
                            entry ->
                                    resolved.append("\n- ")
                                            .append(entry.getKey())
                                            .append(": ")
                                            .append(entry.getValue()));
        }
        var iteration = goal.iteration();
        if (iteration != null
                && iteration.currentIteration() > 1
                && iteration.group().memberTaskNodeIds().contains(subTask.nodeId())
                && iteration.lastEvaluation() != null) {
            resolved.append("\n\n上一轮 evaluator 反馈：").append(iteration.lastEvaluation().reason());
        }
        if (subTask.kind() == Kind.EVALUATOR) {
            resolved.append(
                    "\n\n仅输出严格 JSON：{\"decision\":\"CONTINUE|COMPLETE|BLOCKED\",\"reason\":\"...\"}，禁止额外字段或文本。");
        }
        return resolved.toString();
    }

    public String aggregateResults() {
        var aggregation = goal.aggregationContract();
        var values =
                aggregation.executorOrder().stream()
                        .map(this::requireNode)
                        .map(TaskNode::result)
                        .map(value -> Objects.requireNonNullElse(value, ""))
                        .toList();
        return switch (aggregation.kind()) {
            case PASS_THROUGH -> values.getFirst();
            case ORDERED_CONCAT -> String.join(aggregation.separator(), values);
            case AGGREGATOR_REDUCE -> requireNode("aggregator").result();
        };
    }

    public TaskPlan complete(String nodeId, String result) {
        var current = requireNode(nodeId);
        if (current.status() == Status.COMPLETED) return this;
        if (current.status() != Status.RUNNING) {
            throw new IllegalStateException("只有 RUNNING 子任务可以完成: " + nodeId);
        }
        return update(current.completed(result));
    }

    public TaskPlan evaluateIteration(
            String evaluatorTaskNodeId,
            IterationEvaluation evaluation,
            IterationStopReason boundaryStop,
            Instant evaluatedAt) {
        Objects.requireNonNull(evaluation, "evaluation 不能为空");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt 不能为空");
        var iteration = Objects.requireNonNull(goal.iteration(), "TaskPlan 未声明 IterationGroup");
        if (!iteration.group().evaluatorTaskNodeId().equals(evaluatorTaskNodeId)) {
            throw new IllegalArgumentException("迭代决策必须由声明的 evaluator 提交");
        }
        var evaluator = requireNode(evaluatorTaskNodeId);
        if (evaluator.kind() != Kind.EVALUATOR || evaluator.status() != Status.RUNNING) {
            throw new IllegalStateException("只有 RUNNING evaluator 可以提交迭代决策");
        }
        // 无新进展检测：不依赖 evaluator 自报，按结构化成员结果比对——连续两轮成员结果与上一轮完全一致
        // 即认定未增加新证据，强制停止并升级，避免盲目消耗迭代预算（agent.md#无新进展停止）。
        var currentResults =
                iteration.group().memberTaskNodeIds().stream()
                        .collect(
                                java.util.stream.Collectors.toUnmodifiableMap(
                                        id -> id, id -> requireNode(id).result()));
        var unchanged = currentResults.equals(iteration.lastIterationResults());
        var unchangedStreak = unchanged ? iteration.unchangedStreak() + 1 : 0;
        var copy = new LinkedHashMap<>(nodes);
        copy.put(evaluatorTaskNodeId, evaluator.completed(evaluation.decision().name()));
        var stop = boundaryStop;
        if (stop == null && evaluation.decision() == IterationEvaluation.Decision.BLOCKED) {
            stop = IterationStopReason.BLOCKED;
        }
        if (stop == null && unchangedStreak >= 2) {
            stop = IterationStopReason.NO_PROGRESS;
        }
        if (stop == null
                && evaluation.decision() == IterationEvaluation.Decision.CONTINUE
                && iteration.currentIteration() >= iteration.group().maxIterations()) {
            stop = IterationStopReason.MAX_ITERATIONS;
        }
        if (stop != null || evaluation.decision() == IterationEvaluation.Decision.COMPLETE) {
            return withIteration(
                    copy,
                    new IterationState(
                            iteration.group(),
                            iteration.currentIteration(),
                            evaluation,
                            stop,
                            evaluatedAt,
                            currentResults,
                            unchangedStreak));
        }
        iteration.group().memberTaskNodeIds().stream()
                .map(this::requireNode)
                .forEach(member -> copy.put(member.nodeId(), member.nextIteration()));
        copy.put(evaluatorTaskNodeId, evaluator.nextIteration());
        return withIteration(
                copy,
                new IterationState(
                        iteration.group(),
                        iteration.currentIteration() + 1,
                        evaluation,
                        null,
                        evaluatedAt,
                        currentResults,
                        unchangedStreak));
    }

    private TaskPlan withIteration(Map<String, TaskNode> changed, IterationState nextIteration) {
        return new TaskPlan(
                taskId,
                new Goal(
                        goal.goalId(),
                        goal.description(),
                        goal.completionEvidence(),
                        goal.aggregationContract(),
                        nextIteration),
                maxParallelism,
                changed);
    }

    public TaskPlan fail(String nodeId, String failure, boolean transientFailure) {
        var current = requireNode(nodeId);
        if (current.status() != Status.RUNNING) {
            throw new IllegalStateException("只有 RUNNING 子任务可以失败: " + nodeId);
        }
        return update(current.failed(failure, transientFailure));
    }

    public TaskPlan awaitAuthorization(ExecutionId executionId, SessionId sessionId) {
        var current = requireNode(executionId, sessionId);
        if (current.status() != Status.RUNNING) {
            throw new IllegalStateException("只有 RUNNING 子任务可以等待授权: " + current.nodeId());
        }
        return update(current.awaitingAuthorization());
    }

    public TaskPlan resumeAfterAuthorization(ExecutionId executionId, SessionId sessionId) {
        var current = requireNode(executionId, sessionId);
        if (current.status() != Status.AWAITING_AUTHORIZATION) {
            throw new IllegalStateException("子任务当前不在等待授权状态: " + current.nodeId());
        }
        return update(current.authorizationGranted());
    }

    public TaskPlan awaitClarification(ExecutionId executionId, String nodeId) {
        var current = requireNode(nodeId);
        if (!current.executionId().equals(executionId) || current.status() != Status.RUNNING) {
            throw new IllegalStateException("只有当前 RUNNING 子任务可以等待澄清");
        }
        return update(current.awaitingClarification());
    }

    public TaskPlan resumeAfterClarification(
            ExecutionId executionId, String nodeId, Map<String, String> values) {
        var current = requireNode(nodeId);
        if (!current.executionId().equals(executionId)
                || current.status() != Status.AWAITING_CLARIFICATION) {
            throw new IllegalStateException("子任务当前不在等待澄清状态");
        }
        return update(current.clarificationResolved(values));
    }

    public TaskPlan stopClarification(ExecutionId executionId, String nodeId) {
        var current = requireNode(nodeId);
        if (!current.executionId().equals(executionId)
                || current.status() != Status.AWAITING_CLARIFICATION) {
            throw new IllegalStateException("子任务当前不在等待澄清状态");
        }
        return update(current.clarificationStopped());
    }

    public TaskPlan interrupt(String nodeId, boolean retryable) {
        var current = requireNode(nodeId);
        if (current.status() != Status.RUNNING) {
            throw new IllegalStateException("只有 RUNNING 子任务可以中断: " + nodeId);
        }
        return update(current.interrupted(retryable));
    }

    public TaskPlan update(TaskNode changed) {
        requireNode(changed.nodeId());
        var copy = new LinkedHashMap<>(nodes);
        copy.put(changed.nodeId(), changed);
        return new TaskPlan(taskId, goal, maxParallelism, copy);
    }

    public TaskPlan interruptRunning(boolean retryable) {
        var copy = new LinkedHashMap<String, TaskNode>();
        nodes.forEach(
                (key, task) -> {
                    var shouldInterrupt =
                            task.status() == Status.RUNNING
                                    || task.status() == Status.AWAITING_AUTHORIZATION
                                    || task.status() == Status.AWAITING_CLARIFICATION
                                    || (!retryable
                                            && (task.status() == Status.PENDING
                                                    || task.status() == Status.RETRYABLE));
                    copy.put(key, shouldInterrupt ? task.interrupted(retryable) : task);
                });
        return new TaskPlan(taskId, goal, maxParallelism, copy);
    }

    private TaskNode requireNode(String nodeId) {
        var task = nodes.get(nodeId);
        if (task == null) throw new IllegalArgumentException("子任务不存在: " + nodeId);
        return task;
    }

    private TaskNode requireNode(ExecutionId executionId, SessionId sessionId) {
        Objects.requireNonNull(executionId, "executionId 不能为空");
        Objects.requireNonNull(sessionId, "sessionId 不能为空");
        return nodes.values().stream()
                .filter(
                        task ->
                                task.executionId().equals(executionId)
                                        && task.sessionId().equals(sessionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("当前子任务不存在"));
    }

    private static void validateReferences(Map<String, TaskNode> tasks) {
        tasks.forEach(
                (key, task) -> {
                    if (!key.equals(task.nodeId())) {
                        throw new IllegalArgumentException("nodes key 与 nodeId 不一致: " + key);
                    }
                    task.dependsOn()
                            .forEach(
                                    dependency -> {
                                        if (!tasks.containsKey(dependency)) {
                                            throw new IllegalArgumentException(
                                                    "子任务依赖不存在: " + dependency);
                                        }
                                        if (dependency.equals(task.nodeId())) {
                                            throw new IllegalArgumentException(
                                                    "子任务不能依赖自身: " + dependency);
                                        }
                                    });
                });
    }

    private static void rejectCycles(Map<String, TaskNode> tasks) {
        var indegree = new LinkedHashMap<String, Integer>();
        var dependents = new LinkedHashMap<String, List<String>>();
        tasks.keySet()
                .forEach(
                        key -> {
                            indegree.put(key, 0);
                            dependents.put(key, new ArrayList<>());
                        });
        tasks.values()
                .forEach(
                        task ->
                                task.dependsOn()
                                        .forEach(
                                                dependency -> {
                                                    indegree.compute(
                                                            task.nodeId(),
                                                            (ignored, value) -> value + 1);
                                                    dependents.get(dependency).add(task.nodeId());
                                                }));
        var ready = new ArrayDeque<String>();
        indegree.forEach(
                (key, value) -> {
                    if (value == 0) ready.add(key);
                });
        var visited = 0;
        while (!ready.isEmpty()) {
            var current = ready.removeFirst();
            visited++;
            for (var dependent : dependents.get(current)) {
                var remaining = indegree.compute(dependent, (ignored, value) -> value - 1);
                if (remaining == 0) ready.add(dependent);
            }
        }
        if (visited != tasks.size()) throw new IllegalArgumentException("TaskPlan 子任务依赖存在环");
    }

    public record Goal(
            String goalId,
            String description,
            Set<String> completionEvidence,
            TaskPlanDraft.AggregationContract aggregationContract,
            IterationState iteration) {
        public Goal {
            if (goalId == null
                    || goalId.isBlank()
                    || description == null
                    || description.isBlank()) {
                throw new IllegalArgumentException("goalId 和 description 不能为空白");
            }
            completionEvidence = Set.copyOf(Objects.requireNonNull(completionEvidence, "完成证据不能为空"));
            if (completionEvidence.isEmpty()) {
                throw new IllegalArgumentException("Goal 必须声明完成证据");
            }
            Objects.requireNonNull(aggregationContract, "aggregationContract 不能为空");
        }
    }

    public record IterationState(
            TaskPlanDraft.IterationGroup group,
            int currentIteration,
            IterationEvaluation lastEvaluation,
            IterationStopReason stopReason,
            Instant evaluatedAt,
            Map<String, String> lastIterationResults,
            int unchangedStreak) {
        public IterationState {
            Objects.requireNonNull(group, "iteration group 不能为空");
            if (currentIteration < 1 || currentIteration > group.maxIterations()) {
                throw new IllegalArgumentException("currentIteration 超出 IterationGroup 边界");
            }
            if ((lastEvaluation == null) != (evaluatedAt == null)) {
                throw new IllegalArgumentException("迭代决策和决策时间必须同时存在");
            }
            if (stopReason != null && lastEvaluation == null) {
                throw new IllegalArgumentException("迭代停止必须携带 evaluator 决策");
            }
            lastIterationResults =
                    lastIterationResults == null ? Map.of() : Map.copyOf(lastIterationResults);
            if (unchangedStreak < 0) {
                throw new IllegalArgumentException("unchangedStreak 不能为负数");
            }
        }

        private void validate(Map<String, TaskNode> tasks) {
            if (!tasks.keySet().containsAll(group.memberTaskNodeIds())
                    || !tasks.containsKey(group.evaluatorTaskNodeId())
                    || tasks.get(group.evaluatorTaskNodeId()).kind() != Kind.EVALUATOR) {
                throw new IllegalArgumentException("TaskPlan 迭代组引用无效");
            }
        }
    }

    public record AssistantTarget(
            String assistantId,
            long assistantRevision,
            String roleKey,
            String skillKey,
            Set<String> allowedToolKeys) {
        public AssistantTarget {
            assistantId = requireText(assistantId, "assistantId");
            if (assistantRevision < 0) {
                throw new IllegalArgumentException("assistantRevision 不能小于 0");
            }
            roleKey = requireText(roleKey, "roleKey");
            skillKey = requireText(skillKey, "skillKey");
            allowedToolKeys =
                    Set.copyOf(Objects.requireNonNull(allowedToolKeys, "allowedToolKeys 不能为空"));
            if (allowedToolKeys.stream().anyMatch(value -> value == null || value.isBlank())) {
                throw new IllegalArgumentException("allowedToolKeys 不能包含空值");
            }
        }

        private static String requireText(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(field + " 不能为空白");
            }
            return value.trim();
        }
    }

    public record TaskNode(
            String nodeId,
            Kind kind,
            String description,
            Set<String> dependsOn,
            Map<String, TaskPlanDraft.InputBinding> inputBindings,
            String roleKey,
            String skillKey,
            AssistantTarget assistantTarget,
            TaskModelSelection modelSelection,
            Status status,
            boolean retryable,
            int attempts,
            int maxAttempts,
            ExecutionId executionId,
            SessionId sessionId,
            String result,
            String failure,
            Map<String, String> clarifiedParameters) {
        public TaskNode {
            if (nodeId == null
                    || nodeId.isBlank()
                    || description == null
                    || description.isBlank()) {
                throw new IllegalArgumentException("nodeId 和 description 不能为空白");
            }
            Objects.requireNonNull(kind, "kind 不能为空");
            var dependencyIds = Set.copyOf(Objects.requireNonNull(dependsOn, "dependsOn 不能为空"));
            dependsOn = dependencyIds;
            inputBindings = Map.copyOf(Objects.requireNonNull(inputBindings, "inputBindings 不能为空"));
            clarifiedParameters =
                    Map.copyOf(
                            Objects.requireNonNull(
                                    clarifiedParameters, "clarifiedParameters 不能为空"));
            if (inputBindings.values().stream()
                    .anyMatch(binding -> !dependencyIds.contains(binding.sourceTaskNodeId()))) {
                throw new IllegalArgumentException("inputBindings 只能引用已声明依赖");
            }
            Objects.requireNonNull(modelSelection, "modelSelection 不能为空");
            Objects.requireNonNull(status, "status 不能为空");
            // 协调者必须绑定 Role 用于 Prompt 装配与授权衰减；但不持有业务技能，skillKey 可空
            if (kind == Kind.COORDINATOR && blank(roleKey)) {
                throw new IllegalArgumentException("协调者必须绑定固定 Role");
            }
            if (attempts < 0 || maxAttempts < 1 || attempts > maxAttempts) {
                throw new IllegalArgumentException("子任务尝试次数不合法");
            }
            if ((executionId == null) != (sessionId == null)
                    || (attempts == 0) != (executionId == null)) {
                throw new IllegalArgumentException("未物化节点不得携带 execution/session，已物化节点必须携带完整身份");
            }
            if (status != Status.PENDING && (executionId == null || sessionId == null)) {
                throw new IllegalArgumentException("非 PENDING 节点必须携带 execution/session identity");
            }
            if (status == Status.COMPLETED && result == null) {
                throw new IllegalArgumentException("已完成子任务必须携带结果");
            }
            if ((status == Status.RETRYABLE || status == Status.FAILED) && failure == null) {
                throw new IllegalArgumentException("失败子任务必须携带失败原因");
            }
        }

        public static TaskNode pending(
                String nodeId,
                Kind kind,
                String description,
                Set<String> dependsOn,
                Map<String, TaskPlanDraft.InputBinding> inputBindings,
                String roleKey,
                String skillKey,
                TaskModelSelection modelSelection,
                int maxAttempts) {
            return pending(
                    nodeId,
                    kind,
                    description,
                    dependsOn,
                    inputBindings,
                    roleKey,
                    skillKey,
                    null,
                    modelSelection,
                    maxAttempts);
        }

        /**
         * 是否先规划再执行不再是建板时写死的静态字段（AAF-107 选项 B 架构改造，2026-09-02）：任何节点 （协调者或执行者）在自己的 execution
         * 内自主决定要不要调用 {@code submit_executor_plan}， {@code TaskCommandService.executeNode} 每轮改为运行时查询
         * {@code ExecutorPlanPort.findActive(...)} 判断是否存在活跃计划来决定分流，不再依赖本节点上的固定标志。
         */
        public static TaskNode pending(
                String nodeId,
                Kind kind,
                String description,
                Set<String> dependsOn,
                Map<String, TaskPlanDraft.InputBinding> inputBindings,
                String roleKey,
                String skillKey,
                AssistantTarget assistantTarget,
                TaskModelSelection modelSelection,
                int maxAttempts) {
            return new TaskNode(
                    nodeId,
                    kind,
                    description,
                    dependsOn,
                    inputBindings,
                    roleKey,
                    skillKey,
                    assistantTarget,
                    modelSelection,
                    Status.PENDING,
                    true,
                    0,
                    maxAttempts,
                    null,
                    null,
                    null,
                    null,
                    Map.of());
        }

        public boolean readyForClaim() {
            return status == Status.PENDING || status == Status.READY || status == Status.RETRYABLE;
        }

        private TaskNode completed(String value) {
            return copy(
                    Status.COMPLETED,
                    attempts,
                    executionId,
                    sessionId,
                    Objects.requireNonNullElse(value, ""),
                    null,
                    clarifiedParameters);
        }

        private TaskNode failed(String reason, boolean transientFailure) {
            var canRetry = retryable && transientFailure && attempts < maxAttempts;
            return copy(
                    canRetry ? Status.RETRYABLE : Status.FAILED,
                    attempts,
                    executionId,
                    sessionId,
                    null,
                    Objects.requireNonNullElse(reason, "子任务失败"),
                    clarifiedParameters);
        }

        private TaskNode awaitingAuthorization() {
            return copy(
                    Status.AWAITING_AUTHORIZATION,
                    attempts,
                    executionId,
                    sessionId,
                    null,
                    null,
                    clarifiedParameters);
        }

        private TaskNode authorizationGranted() {
            return copy(
                    Status.READY,
                    attempts,
                    executionId,
                    sessionId,
                    null,
                    null,
                    clarifiedParameters);
        }

        private TaskNode awaitingClarification() {
            return copy(
                    Status.AWAITING_CLARIFICATION,
                    attempts,
                    executionId,
                    sessionId,
                    null,
                    null,
                    clarifiedParameters);
        }

        private TaskNode clarificationResolved(Map<String, String> values) {
            var merged = new LinkedHashMap<>(clarifiedParameters);
            merged.putAll(values);
            return copy(
                    Status.READY, attempts, executionId, sessionId, null, null, Map.copyOf(merged));
        }

        private TaskNode clarificationStopped() {
            return copy(
                    Status.CANCELED,
                    attempts,
                    executionId,
                    sessionId,
                    result,
                    failure,
                    clarifiedParameters);
        }

        private TaskNode nextIteration() {
            return copy(Status.PENDING, 0, null, null, null, null, clarifiedParameters);
        }

        private TaskNode interrupted(boolean allowRetry) {
            var canRetry = allowRetry && retryable;
            var retainedAttempts = canRetry ? Math.max(0, attempts - 1) : attempts;
            return copy(
                    canRetry ? Status.RETRYABLE : Status.CANCELED,
                    retainedAttempts,
                    executionId,
                    sessionId,
                    result,
                    canRetry ? "执行被控制操作中断" : failure,
                    clarifiedParameters);
        }

        private TaskNode copy(
                Status nextStatus,
                int nextAttempts,
                ExecutionId nextExecution,
                SessionId nextSession,
                String nextResult,
                String nextFailure,
                Map<String, String> nextParameters) {
            return new TaskNode(
                    nodeId,
                    kind,
                    description,
                    dependsOn,
                    inputBindings,
                    roleKey,
                    skillKey,
                    assistantTarget,
                    modelSelection,
                    nextStatus,
                    retryable,
                    nextAttempts,
                    maxAttempts,
                    nextExecution,
                    nextSession,
                    nextResult,
                    nextFailure,
                    nextParameters);
        }

        private static boolean blank(String value) {
            return value == null || value.isBlank();
        }

        public enum Kind {
            COORDINATOR,
            EXECUTOR,
            EVALUATOR,
            AGGREGATOR
        }
    }

    public enum IterationStopReason {
        BLOCKED,
        MAX_ITERATIONS,
        DEADLINE_REACHED,
        BUDGET_EXHAUSTED,
        NO_PROGRESS
    }

    public enum Status {
        PENDING,
        READY,
        CLAIMED,
        RUNNING,
        AWAITING_AUTHORIZATION,
        AWAITING_CLARIFICATION,
        PAUSED,
        VERIFYING,
        COMPLETED,
        RETRYABLE,
        FAILED,
        CANCELED,
        BLOCKED
    }
}
