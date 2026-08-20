package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.xuejiai.aaf.framework.intelligent.assistant.model.CoordinationPlan.ExecutorAssignment;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskBoard.SubTask.Kind;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;

/** PostgreSQL 持久化的目标、迭代状态与 AAF 管理的子 Agent 依赖 DAG。 */
public record TaskBoard(
        TaskId taskId, Goal goal, int maxParallelism, Map<String, SubTask> subTasks) {

    public TaskBoard {
        Objects.requireNonNull(taskId, "taskId 不能为空");
        Objects.requireNonNull(goal, "goal 不能为空");
        if (maxParallelism < 1) throw new IllegalArgumentException("maxParallelism 必须大于 0");
        var frozenSubTasks = Map.copyOf(Objects.requireNonNull(subTasks, "subTasks 不能为空"));
        subTasks = frozenSubTasks;
        if (frozenSubTasks.isEmpty()) throw new IllegalArgumentException("TaskBoard 至少包含一个子任务");
        validateReferences(frozenSubTasks);
        goal.completionEvidence()
                .forEach(
                        evidence -> {
                            if (!frozenSubTasks.containsKey(evidence)) {
                                throw new IllegalArgumentException(
                                        "Goal 完成证据引用不存在的子任务: " + evidence);
                            }
                        });
        if (goal.iteration() != null) goal.iteration().validate(frozenSubTasks);
        rejectCycles(frozenSubTasks);
        var running =
                frozenSubTasks.values().stream()
                        .filter(task -> task.status() == Status.RUNNING)
                        .count();
        if (running > maxParallelism) throw new IllegalArgumentException("运行中子任务超过并行度限制");
    }

    /** 非协调任务的单执行者看板。 */
    public static TaskBoard single(TaskId taskId, String description, int maxAttempts) {
        var subTask =
                SubTask.pending(
                        "root",
                        Kind.EXECUTOR,
                        description,
                        Set.of(),
                        Map.of(),
                        null,
                        null,
                        TaskModelSelection.auto(),
                        maxAttempts);
        return new TaskBoard(
                taskId,
                new Goal(
                        "goal",
                        description,
                        Set.of("root"),
                        CoordinationPlan.AggregationContract.passThrough("root"),
                        null),
                1,
                Map.of(subTask.subTaskId(), subTask));
    }

    /** 固定 Route 任务：先且仅先运行无业务工具的协调者。 */
    public static TaskBoard coordinated(
            TaskId taskId, String description, String roleKey, String skillKey, int maxAttempts) {
        var coordinator =
                SubTask.pending(
                        "coordinator",
                        Kind.COORDINATOR,
                        description,
                        Set.of(),
                        Map.of(),
                        roleKey,
                        skillKey,
                        TaskModelSelection.auto(),
                        maxAttempts);
        return new TaskBoard(
                taskId,
                new Goal(
                        "goal",
                        description,
                        Set.of("coordinator"),
                        CoordinationPlan.AggregationContract.passThrough("coordinator"),
                        null),
                1,
                Map.of(coordinator.subTaskId(), coordinator));
    }

    /** Leader 协调的静态 Team；计划只能分配到已冻结 Worker key。 */
    public static TaskBoard teamCoordinated(
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
                        + "\n\n静态 Team Worker（executors.subTaskId/roleKey/skillKey 必须逐项匹配）："
                        + workerSummary;
        var subTasks = new LinkedHashMap<String, SubTask>();
        var coordinator =
                SubTask.pending(
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
        subTasks.put(coordinator.subTaskId(), coordinator);
        workers.forEach(
                (memberKey, target) ->
                        subTasks.put(
                                memberKey,
                                SubTask.pending(
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
        return new TaskBoard(
                taskId,
                new Goal(
                        "goal",
                        description,
                        Set.copyOf(workerKeys),
                        CoordinationPlan.AggregationContract.orderedConcat(workerKeys, "\n"),
                        null),
                Math.min(workers.size(), 8),
                subTasks);
    }

    /** 将已校验计划固化为协调者完成及其执行者 DAG。 */
    public TaskBoard applyCoordinationPlan(CoordinationPlan plan) {
        Objects.requireNonNull(plan, "plan 不能为空");
        var coordinator = requireSubTask("coordinator");
        if (coordinator.kind() != Kind.COORDINATOR || coordinator.status() != Status.RUNNING) {
            throw new IllegalStateException("仅运行中的协调者可以冻结执行计划");
        }
        var teamTargets =
                subTasks.values().stream()
                        .filter(task -> task.kind() == Kind.EXECUTOR)
                        .filter(task -> task.assistantTarget() != null)
                        .collect(
                                java.util.stream.Collectors.toUnmodifiableMap(
                                        SubTask::subTaskId, SubTask::assistantTarget));
        var teamBoard = coordinator.assistantTarget() != null && !teamTargets.isEmpty();
        if (teamBoard) {
            var plannedKeys =
                    plan.executors().stream()
                            .map(ExecutorAssignment::subTaskId)
                            .collect(java.util.stream.Collectors.toUnmodifiableSet());
            if (!plannedKeys.equals(teamTargets.keySet())) {
                throw new IllegalArgumentException("Team 协调计划必须且只能覆盖全部静态 Worker");
            }
        }
        var copy = new LinkedHashMap<String, SubTask>();
        copy.put(coordinator.subTaskId(), coordinator.completed(plan.goal()));
        for (ExecutorAssignment assignment : plan.executors()) {
            if (copy.containsKey(assignment.subTaskId())) {
                throw new IllegalArgumentException("协调计划重复子任务标识: " + assignment.subTaskId());
            }
            var target = teamBoard ? teamTargets.get(assignment.subTaskId()) : null;
            if (target != null
                    && (!target.roleKey().equals(assignment.roleKey())
                            || !target.skillKey().equals(assignment.skillKey()))) {
                throw new IllegalArgumentException("Team 协调计划不能改变 Worker 固定 Role/Skill");
            }
            var kind =
                    plan.iterationGroup() != null
                                    && plan.iterationGroup()
                                            .evaluatorSubTaskId()
                                            .equals(assignment.subTaskId())
                            ? Kind.EVALUATOR
                            : Kind.EXECUTOR;
            copy.put(
                    assignment.subTaskId(),
                    SubTask.pending(
                            assignment.subTaskId(),
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
        var completionEvidence = Set.copyOf(plan.aggregationContract().executorOrder());
        if (plan.aggregationContract().kind()
                == CoordinationPlan.AggregationContract.Kind.COORDINATOR_REDUCE) {
            var aggregatorId = "aggregator";
            if (copy.containsKey(aggregatorId)) {
                throw new IllegalArgumentException("协调计划不能使用保留子任务标识: " + aggregatorId);
            }
            var bindings = new LinkedHashMap<String, CoordinationPlan.InputBinding>();
            plan.aggregationContract()
                    .executorOrder()
                    .forEach(
                            executorId ->
                                    bindings.put(
                                            "result." + executorId,
                                            new CoordinationPlan.InputBinding(executorId)));
            var dependencies = new java.util.HashSet<>(plan.aggregationContract().executorOrder());
            if (plan.iterationGroup() != null) {
                dependencies.add(plan.iterationGroup().evaluatorSubTaskId());
            }
            copy.put(
                    aggregatorId,
                    SubTask.pending(
                            aggregatorId,
                            Kind.AGGREGATOR,
                            "根据已完成执行者结果生成最终聚合输出。",
                            Set.copyOf(dependencies),
                            bindings,
                            coordinator.roleKey(),
                            coordinator.skillKey(),
                            coordinator.assistantTarget(),
                            coordinator.modelSelection(),
                            1));
            completionEvidence = Set.of(aggregatorId);
        }
        var iteration =
                plan.iterationGroup() == null
                        ? null
                        : new IterationState(plan.iterationGroup(), 1, null, null, null);
        return new TaskBoard(
                taskId,
                new Goal(
                        "goal",
                        plan.goal(),
                        completionEvidence,
                        plan.aggregationContract(),
                        iteration),
                plan.maxParallelism(),
                copy);
    }

    public List<SubTask> ready() {
        if (goal.iteration() != null && goal.iteration().stopReason() != null) return List.of();
        var slots =
                maxParallelism
                        - (int)
                                subTasks.values().stream()
                                        .filter(task -> task.status() == Status.RUNNING)
                                        .count();
        if (slots <= 0) return List.of();
        return subTasks.values().stream()
                .filter(SubTask::readyForClaim)
                .filter(this::allowedByIteration)
                .filter(
                        task ->
                                task.dependsOn().stream()
                                        .allMatch(
                                                dependency ->
                                                        subTasks.get(dependency).status()
                                                                == Status.COMPLETED))
                .sorted(java.util.Comparator.comparing(SubTask::subTaskId))
                .limit(slots)
                .toList();
    }

    private boolean allowedByIteration(SubTask task) {
        var iteration = goal.iteration();
        if (iteration == null
                || (iteration.lastEvaluation() != null
                        && iteration.lastEvaluation().decision()
                                == IterationEvaluation.Decision.COMPLETE)) {
            return true;
        }
        return iteration.group().memberSubTaskIds().contains(task.subTaskId())
                || iteration.group().evaluatorSubTaskId().equals(task.subTaskId());
    }

    public ReadyClaim claimReady() {
        var selected = ready();
        if (selected.isEmpty()) return new ReadyClaim(this, List.of());
        var copy = new LinkedHashMap<>(subTasks);
        var claimed = selected.stream().map(SubTask::claim).toList();
        claimed.forEach(task -> copy.put(task.subTaskId(), task));
        return new ReadyClaim(new TaskBoard(taskId, goal, maxParallelism, copy), claimed);
    }

    public boolean completed() {
        return (goal.iteration() == null || goal.iteration().stopReason() == null)
                && subTasks.values().stream().allMatch(task -> task.status() == Status.COMPLETED)
                && goal.completionEvidence().stream()
                        .allMatch(id -> subTasks.get(id).status() == Status.COMPLETED);
    }

    public boolean hasTerminalFailure() {
        return subTasks.values().stream().anyMatch(task -> task.status() == Status.FAILED);
    }

    public boolean hasRunning() {
        return subTasks.values().stream().anyMatch(task -> task.status() == Status.RUNNING);
    }

    public String resolveInput(SubTask subTask) {
        Objects.requireNonNull(subTask, "subTask 不能为空");
        var resolved = new StringBuilder(subTask.description());
        if (!subTask.inputBindings().isEmpty()) {
            resolved.append("\n\n已冻结输入绑定：");
            subTask.inputBindings().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(
                            entry -> {
                                var source = requireSubTask(entry.getValue().sourceSubTaskId());
                                if (source.status() != Status.COMPLETED) {
                                    throw new IllegalStateException(
                                            "输入绑定来源尚未完成: " + source.subTaskId());
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
                && iteration.group().memberSubTaskIds().contains(subTask.subTaskId())
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
                        .map(this::requireSubTask)
                        .map(SubTask::result)
                        .map(value -> Objects.requireNonNullElse(value, ""))
                        .toList();
        return switch (aggregation.kind()) {
            case PASS_THROUGH -> values.getFirst();
            case ORDERED_CONCAT -> String.join(aggregation.separator(), values);
            case COORDINATOR_REDUCE -> requireSubTask("aggregator").result();
        };
    }

    public TaskBoard complete(String subTaskId, String result) {
        var current = requireSubTask(subTaskId);
        if (current.status() == Status.COMPLETED) return this;
        if (current.status() != Status.RUNNING) {
            throw new IllegalStateException("只有 RUNNING 子任务可以完成: " + subTaskId);
        }
        return update(current.completed(result));
    }

    public TaskBoard evaluateIteration(
            String evaluatorSubTaskId,
            IterationEvaluation evaluation,
            IterationStopReason boundaryStop,
            Instant evaluatedAt) {
        Objects.requireNonNull(evaluation, "evaluation 不能为空");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt 不能为空");
        var iteration = Objects.requireNonNull(goal.iteration(), "TaskBoard 未声明 IterationGroup");
        if (!iteration.group().evaluatorSubTaskId().equals(evaluatorSubTaskId)) {
            throw new IllegalArgumentException("迭代决策必须由声明的 evaluator 提交");
        }
        var evaluator = requireSubTask(evaluatorSubTaskId);
        if (evaluator.kind() != Kind.EVALUATOR || evaluator.status() != Status.RUNNING) {
            throw new IllegalStateException("只有 RUNNING evaluator 可以提交迭代决策");
        }
        var copy = new LinkedHashMap<>(subTasks);
        copy.put(evaluatorSubTaskId, evaluator.completed(evaluation.decision().name()));
        var stop = boundaryStop;
        if (stop == null && evaluation.decision() == IterationEvaluation.Decision.BLOCKED) {
            stop = IterationStopReason.BLOCKED;
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
                            evaluatedAt));
        }
        iteration.group().memberSubTaskIds().stream()
                .map(this::requireSubTask)
                .forEach(member -> copy.put(member.subTaskId(), member.nextIteration()));
        copy.put(evaluatorSubTaskId, evaluator.nextIteration());
        return withIteration(
                copy,
                new IterationState(
                        iteration.group(),
                        iteration.currentIteration() + 1,
                        evaluation,
                        null,
                        evaluatedAt));
    }

    private TaskBoard withIteration(Map<String, SubTask> changed, IterationState nextIteration) {
        return new TaskBoard(
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

    public TaskBoard fail(String subTaskId, String failure, boolean transientFailure) {
        var current = requireSubTask(subTaskId);
        if (current.status() != Status.RUNNING) {
            throw new IllegalStateException("只有 RUNNING 子任务可以失败: " + subTaskId);
        }
        return update(current.failed(failure, transientFailure));
    }

    public TaskBoard awaitAuthorization(ExecutionId executionId, SessionId sessionId) {
        var current = requireSubTask(executionId, sessionId);
        if (current.status() != Status.RUNNING) {
            throw new IllegalStateException("只有 RUNNING 子任务可以等待授权: " + current.subTaskId());
        }
        return update(current.awaitingAuthorization());
    }

    public TaskBoard resumeAfterAuthorization(ExecutionId executionId, SessionId sessionId) {
        var current = requireSubTask(executionId, sessionId);
        if (current.status() != Status.AWAITING_AUTHORIZATION) {
            throw new IllegalStateException("子任务当前不在等待授权状态: " + current.subTaskId());
        }
        return update(current.authorizationGranted());
    }

    public TaskBoard awaitClarification(ExecutionId executionId, String subTaskId) {
        var current = requireSubTask(subTaskId);
        if (!current.executionId().equals(executionId) || current.status() != Status.RUNNING) {
            throw new IllegalStateException("只有当前 RUNNING 子任务可以等待澄清");
        }
        return update(current.awaitingClarification());
    }

    public TaskBoard resumeAfterClarification(
            ExecutionId executionId, String subTaskId, Map<String, String> values) {
        var current = requireSubTask(subTaskId);
        if (!current.executionId().equals(executionId)
                || current.status() != Status.AWAITING_CLARIFICATION) {
            throw new IllegalStateException("子任务当前不在等待澄清状态");
        }
        return update(current.clarificationResolved(values));
    }

    public TaskBoard stopClarification(ExecutionId executionId, String subTaskId) {
        var current = requireSubTask(subTaskId);
        if (!current.executionId().equals(executionId)
                || current.status() != Status.AWAITING_CLARIFICATION) {
            throw new IllegalStateException("子任务当前不在等待澄清状态");
        }
        return update(current.clarificationStopped());
    }

    public TaskBoard interrupt(String subTaskId, boolean retryable) {
        var current = requireSubTask(subTaskId);
        if (current.status() != Status.RUNNING) {
            throw new IllegalStateException("只有 RUNNING 子任务可以中断: " + subTaskId);
        }
        return update(current.interrupted(retryable));
    }

    public TaskBoard update(SubTask changed) {
        requireSubTask(changed.subTaskId());
        var copy = new LinkedHashMap<>(subTasks);
        copy.put(changed.subTaskId(), changed);
        return new TaskBoard(taskId, goal, maxParallelism, copy);
    }

    public TaskBoard interruptRunning(boolean retryable) {
        var copy = new LinkedHashMap<String, SubTask>();
        subTasks.forEach(
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
        return new TaskBoard(taskId, goal, maxParallelism, copy);
    }

    private SubTask requireSubTask(String subTaskId) {
        var task = subTasks.get(subTaskId);
        if (task == null) throw new IllegalArgumentException("子任务不存在: " + subTaskId);
        return task;
    }

    private SubTask requireSubTask(ExecutionId executionId, SessionId sessionId) {
        Objects.requireNonNull(executionId, "executionId 不能为空");
        Objects.requireNonNull(sessionId, "sessionId 不能为空");
        return subTasks.values().stream()
                .filter(
                        task ->
                                task.executionId().equals(executionId)
                                        && task.sessionId().equals(sessionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("当前子任务不存在"));
    }

    private static void validateReferences(Map<String, SubTask> tasks) {
        tasks.forEach(
                (key, task) -> {
                    if (!key.equals(task.subTaskId())) {
                        throw new IllegalArgumentException("subTasks key 与 subTaskId 不一致: " + key);
                    }
                    task.dependsOn()
                            .forEach(
                                    dependency -> {
                                        if (!tasks.containsKey(dependency)) {
                                            throw new IllegalArgumentException(
                                                    "子任务依赖不存在: " + dependency);
                                        }
                                        if (dependency.equals(task.subTaskId())) {
                                            throw new IllegalArgumentException(
                                                    "子任务不能依赖自身: " + dependency);
                                        }
                                    });
                });
    }

    private static void rejectCycles(Map<String, SubTask> tasks) {
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
                                                            task.subTaskId(),
                                                            (ignored, value) -> value + 1);
                                                    dependents
                                                            .get(dependency)
                                                            .add(task.subTaskId());
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
        if (visited != tasks.size()) throw new IllegalArgumentException("TaskBoard 子任务依赖存在环");
    }

    private static String randomId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public record ReadyClaim(TaskBoard board, List<SubTask> subTasks) {
        public ReadyClaim {
            Objects.requireNonNull(board, "board 不能为空");
            subTasks = List.copyOf(Objects.requireNonNull(subTasks, "subTasks 不能为空"));
        }
    }

    public record Goal(
            String goalId,
            String description,
            Set<String> completionEvidence,
            CoordinationPlan.AggregationContract aggregationContract,
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
            CoordinationPlan.IterationGroup group,
            int currentIteration,
            IterationEvaluation lastEvaluation,
            IterationStopReason stopReason,
            Instant evaluatedAt) {
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
        }

        private void validate(Map<String, SubTask> tasks) {
            if (!tasks.keySet().containsAll(group.memberSubTaskIds())
                    || !tasks.containsKey(group.evaluatorSubTaskId())
                    || tasks.get(group.evaluatorSubTaskId()).kind() != Kind.EVALUATOR) {
                throw new IllegalArgumentException("TaskBoard 迭代组引用无效");
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

    public record SubTask(
            String subTaskId,
            Kind kind,
            String description,
            Set<String> dependsOn,
            Map<String, CoordinationPlan.InputBinding> inputBindings,
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
        public SubTask {
            if (subTaskId == null
                    || subTaskId.isBlank()
                    || description == null
                    || description.isBlank()) {
                throw new IllegalArgumentException("subTaskId 和 description 不能为空白");
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
                    .anyMatch(binding -> !dependencyIds.contains(binding.sourceSubTaskId()))) {
                throw new IllegalArgumentException("inputBindings 只能引用已声明依赖");
            }
            Objects.requireNonNull(modelSelection, "modelSelection 不能为空");
            Objects.requireNonNull(status, "status 不能为空");
            if (kind == Kind.COORDINATOR && (blank(roleKey) || blank(skillKey))) {
                throw new IllegalArgumentException("协调者必须绑定固定 Role 和 Skill");
            }
            if (attempts < 0 || maxAttempts < 1 || attempts > maxAttempts) {
                throw new IllegalArgumentException("子任务尝试次数不合法");
            }
            Objects.requireNonNull(executionId, "子 Agent executionId 不能为空");
            Objects.requireNonNull(sessionId, "子 Agent sessionId 不能为空");
            if (status == Status.COMPLETED && result == null) {
                throw new IllegalArgumentException("已完成子任务必须携带结果");
            }
            if ((status == Status.RETRYABLE || status == Status.FAILED) && failure == null) {
                throw new IllegalArgumentException("失败子任务必须携带失败原因");
            }
        }

        public static SubTask pending(
                String subTaskId,
                Kind kind,
                String description,
                Set<String> dependsOn,
                Map<String, CoordinationPlan.InputBinding> inputBindings,
                String roleKey,
                String skillKey,
                TaskModelSelection modelSelection,
                int maxAttempts) {
            return pending(
                    subTaskId,
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

        public static SubTask pending(
                String subTaskId,
                Kind kind,
                String description,
                Set<String> dependsOn,
                Map<String, CoordinationPlan.InputBinding> inputBindings,
                String roleKey,
                String skillKey,
                AssistantTarget assistantTarget,
                TaskModelSelection modelSelection,
                int maxAttempts) {
            return new SubTask(
                    subTaskId,
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
                    new ExecutionId(randomId()),
                    new SessionId(randomId()),
                    null,
                    null,
                    Map.of());
        }

        public boolean readyForClaim() {
            return status == Status.PENDING || status == Status.RETRYABLE;
        }

        private SubTask claim() {
            if (!readyForClaim()) {
                throw new IllegalStateException("子任务当前不可 claim: " + subTaskId);
            }
            var nextExecution =
                    status == Status.RETRYABLE ? new ExecutionId(randomId()) : executionId;
            var nextSession = status == Status.RETRYABLE ? new SessionId(randomId()) : sessionId;
            return copy(
                    Status.RUNNING,
                    attempts + 1,
                    nextExecution,
                    nextSession,
                    null,
                    null,
                    clarifiedParameters);
        }

        private SubTask completed(String value) {
            return copy(
                    Status.COMPLETED,
                    attempts,
                    executionId,
                    sessionId,
                    Objects.requireNonNullElse(value, ""),
                    null,
                    clarifiedParameters);
        }

        private SubTask failed(String reason, boolean transientFailure) {
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

        private SubTask awaitingAuthorization() {
            return copy(
                    Status.AWAITING_AUTHORIZATION,
                    attempts,
                    executionId,
                    sessionId,
                    null,
                    null,
                    clarifiedParameters);
        }

        private SubTask authorizationGranted() {
            return copy(
                    Status.PENDING,
                    Math.max(0, attempts - 1),
                    executionId,
                    sessionId,
                    null,
                    null,
                    clarifiedParameters);
        }

        private SubTask awaitingClarification() {
            return copy(
                    Status.AWAITING_CLARIFICATION,
                    attempts,
                    executionId,
                    sessionId,
                    null,
                    null,
                    clarifiedParameters);
        }

        private SubTask clarificationResolved(Map<String, String> values) {
            var merged = new LinkedHashMap<>(clarifiedParameters);
            merged.putAll(values);
            return copy(
                    Status.PENDING,
                    Math.max(0, attempts - 1),
                    executionId,
                    sessionId,
                    null,
                    null,
                    Map.copyOf(merged));
        }

        private SubTask clarificationStopped() {
            return copy(
                    Status.CANCELED,
                    attempts,
                    executionId,
                    sessionId,
                    result,
                    failure,
                    clarifiedParameters);
        }

        private SubTask nextIteration() {
            return copy(
                    Status.PENDING,
                    0,
                    new ExecutionId(randomId()),
                    new SessionId(randomId()),
                    null,
                    null,
                    clarifiedParameters);
        }

        private SubTask interrupted(boolean allowRetry) {
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

        private SubTask copy(
                Status nextStatus,
                int nextAttempts,
                ExecutionId nextExecution,
                SessionId nextSession,
                String nextResult,
                String nextFailure,
                Map<String, String> nextParameters) {
            return new SubTask(
                    subTaskId,
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
        BUDGET_EXHAUSTED
    }

    public enum Status {
        PENDING,
        RUNNING,
        AWAITING_AUTHORIZATION,
        AWAITING_CLARIFICATION,
        COMPLETED,
        RETRYABLE,
        FAILED,
        CANCELED
    }
}
