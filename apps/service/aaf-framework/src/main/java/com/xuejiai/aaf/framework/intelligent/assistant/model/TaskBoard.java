package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;

/** PostgreSQL 持久化的目标与子任务依赖 DAG。 */
public record TaskBoard(
        TaskId taskId, Goal goal, int maxParallelism, Map<String, SubTask> subTasks) {

    public TaskBoard {
        Objects.requireNonNull(taskId, "taskId 不能为空");
        Objects.requireNonNull(goal, "goal 不能为空");
        if (maxParallelism < 1) {
            throw new IllegalArgumentException("maxParallelism 必须大于 0");
        }
        subTasks = Map.copyOf(Objects.requireNonNull(subTasks, "subTasks 不能为空"));
        if (subTasks.isEmpty()) {
            throw new IllegalArgumentException("TaskBoard 至少包含一个子任务");
        }
        validateReferences(subTasks);
        var effectiveSubTasks = subTasks;
        goal.completionEvidence()
                .forEach(
                        evidence -> {
                            if (!effectiveSubTasks.containsKey(evidence)) {
                                throw new IllegalArgumentException(
                                        "Goal 完成证据引用不存在的子任务: " + evidence);
                            }
                        });
        rejectCycles(subTasks);
        var running =
                subTasks.values().stream().filter(task -> task.status() == Status.RUNNING).count();
        if (running > maxParallelism) {
            throw new IllegalArgumentException("运行中子任务超过并行度限制");
        }
    }

    public static TaskBoard single(TaskId taskId, String description, int maxAttempts) {
        var subTask = SubTask.pending("root", description, Set.of(), maxAttempts);
        return new TaskBoard(
                taskId,
                new Goal("goal", description, Set.of("root")),
                1,
                Map.of(subTask.subTaskId(), subTask));
    }

    public List<SubTask> ready() {
        var slots =
                maxParallelism
                        - (int)
                                subTasks.values().stream()
                                        .filter(task -> task.status() == Status.RUNNING)
                                        .count();
        if (slots <= 0) return List.of();
        return subTasks.values().stream()
                .filter(SubTask::readyForClaim)
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

    public ReadyClaim claimReady() {
        var selected = ready();
        if (selected.isEmpty()) return new ReadyClaim(this, List.of());
        var copy = new LinkedHashMap<>(subTasks);
        var claimed = selected.stream().map(SubTask::claim).toList();
        claimed.forEach(task -> copy.put(task.subTaskId(), task));
        return new ReadyClaim(new TaskBoard(taskId, goal, maxParallelism, copy), claimed);
    }

    public boolean completed() {
        return subTasks.values().stream().allMatch(task -> task.status() == Status.COMPLETED)
                && goal.completionEvidence().stream()
                        .allMatch(id -> subTasks.get(id).status() == Status.COMPLETED);
    }

    public boolean hasTerminalFailure() {
        return subTasks.values().stream().anyMatch(task -> task.status() == Status.FAILED);
    }

    public boolean hasRunning() {
        return subTasks.values().stream().anyMatch(task -> task.status() == Status.RUNNING);
    }

    public TaskBoard complete(String subTaskId, String result) {
        var current = requireSubTask(subTaskId);
        if (current.status() == Status.COMPLETED) return this;
        if (current.status() != Status.RUNNING) {
            throw new IllegalStateException("只有 RUNNING 子任务可以完成: " + subTaskId);
        }
        return update(current.completed(result));
    }

    public TaskBoard fail(String subTaskId, String failure, boolean transientFailure) {
        var current = requireSubTask(subTaskId);
        if (current.status() != Status.RUNNING) {
            throw new IllegalStateException("只有 RUNNING 子任务可以失败: " + subTaskId);
        }
        return update(current.failed(failure, transientFailure));
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
        if (visited != tasks.size()) {
            throw new IllegalArgumentException("TaskBoard 子任务依赖存在环");
        }
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

    public record Goal(String goalId, String description, Set<String> completionEvidence) {
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
        }
    }

    public record SubTask(
            String subTaskId,
            String description,
            Set<String> dependsOn,
            Status status,
            boolean retryable,
            int attempts,
            int maxAttempts,
            ExecutionId executionId,
            SessionId sessionId,
            String result,
            String failure) {
        public SubTask {
            if (subTaskId == null
                    || subTaskId.isBlank()
                    || description == null
                    || description.isBlank()) {
                throw new IllegalArgumentException("subTaskId 和 description 不能为空白");
            }
            dependsOn = Set.copyOf(Objects.requireNonNull(dependsOn, "dependsOn 不能为空"));
            Objects.requireNonNull(status, "status 不能为空");
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
                String subTaskId, String description, Set<String> dependsOn, int maxAttempts) {
            return new SubTask(
                    subTaskId,
                    description,
                    dependsOn,
                    Status.PENDING,
                    true,
                    0,
                    maxAttempts,
                    new ExecutionId(randomId()),
                    new SessionId(randomId()),
                    null,
                    null);
        }

        public boolean readyForClaim() {
            return status == Status.PENDING || status == Status.RETRYABLE;
        }

        private SubTask claim() {
            if (!readyForClaim()) throw new IllegalStateException("子任务当前不可 claim: " + subTaskId);
            var nextExecution =
                    status == Status.RETRYABLE ? new ExecutionId(randomId()) : executionId;
            var nextSession = status == Status.RETRYABLE ? new SessionId(randomId()) : sessionId;
            return new SubTask(
                    subTaskId,
                    description,
                    dependsOn,
                    Status.RUNNING,
                    retryable,
                    attempts + 1,
                    maxAttempts,
                    nextExecution,
                    nextSession,
                    null,
                    null);
        }

        private SubTask completed(String value) {
            return new SubTask(
                    subTaskId,
                    description,
                    dependsOn,
                    Status.COMPLETED,
                    retryable,
                    attempts,
                    maxAttempts,
                    executionId,
                    sessionId,
                    Objects.requireNonNullElse(value, ""),
                    null);
        }

        private SubTask failed(String reason, boolean transientFailure) {
            var canRetry = retryable && transientFailure && attempts < maxAttempts;
            return new SubTask(
                    subTaskId,
                    description,
                    dependsOn,
                    canRetry ? Status.RETRYABLE : Status.FAILED,
                    retryable,
                    attempts,
                    maxAttempts,
                    executionId,
                    sessionId,
                    null,
                    Objects.requireNonNullElse(reason, "子任务失败"));
        }

        private SubTask interrupted(boolean allowRetry) {
            var canRetry = allowRetry && retryable;
            var retainedAttempts = canRetry ? Math.max(0, attempts - 1) : attempts;
            return new SubTask(
                    subTaskId,
                    description,
                    dependsOn,
                    canRetry ? Status.RETRYABLE : Status.CANCELED,
                    retryable,
                    retainedAttempts,
                    maxAttempts,
                    executionId,
                    sessionId,
                    result,
                    canRetry ? "执行被控制操作中断" : failure);
        }
    }

    public enum Status {
        PENDING,
        RUNNING,
        COMPLETED,
        RETRYABLE,
        FAILED,
        CANCELED
    }
}
