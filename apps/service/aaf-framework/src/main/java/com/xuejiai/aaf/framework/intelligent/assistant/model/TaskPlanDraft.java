package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 协调者输出的严格结构化执行计划。 */
public record TaskPlanDraft(
        String goal,
        int maxParallelism,
        AggregationContract aggregationContract,
        List<ExecutorAssignment> executors,
        IterationGroup iterationGroup) {
    public TaskPlanDraft {
        goal = requireText(goal, "goal");
        if (maxParallelism < 1 || maxParallelism > 8) {
            throw new IllegalArgumentException("maxParallelism 必须在 1..8");
        }
        Objects.requireNonNull(aggregationContract, "aggregationContract 不能为空");
        executors = List.copyOf(Objects.requireNonNull(executors, "executors 不能为空"));
        if (executors.isEmpty() || executors.size() > 8) {
            throw new IllegalArgumentException("协调计划必须包含 1..8 个执行者");
        }
        var assignments = new LinkedHashMap<String, ExecutorAssignment>();
        executors.forEach(
                assignment -> {
                    if (assignments.put(assignment.nodeId(), assignment) != null) {
                        throw new IllegalArgumentException("协调计划的执行者标识必须唯一");
                    }
                });
        var ids = Set.copyOf(assignments.keySet());
        executors.forEach(
                assignment ->
                        assignment.dependsOn().stream()
                                .filter(dependency -> !"coordinator".equals(dependency))
                                .filter(dependency -> !ids.contains(dependency))
                                .findFirst()
                                .ifPresent(
                                        dependency -> {
                                            throw new IllegalArgumentException(
                                                    "协调计划引用不存在的依赖: " + dependency);
                                        }));
        rejectCycles(assignments);
        var aggregationIds = ids;
        if (iterationGroup != null) {
            iterationGroup.validate(assignments);
            var evaluatorId = iterationGroup.evaluatorTaskNodeId();
            aggregationIds =
                    ids.stream()
                            .filter(id -> !id.equals(evaluatorId))
                            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        aggregationContract.validate(aggregationIds);
    }

    private static void rejectCycles(Map<String, ExecutorAssignment> assignments) {
        var indegree = new LinkedHashMap<String, Integer>();
        var dependents = new LinkedHashMap<String, List<String>>();
        assignments
                .keySet()
                .forEach(
                        id -> {
                            indegree.put(id, 0);
                            dependents.put(id, new ArrayList<>());
                        });
        assignments
                .values()
                .forEach(
                        assignment ->
                                assignment.dependsOn().stream()
                                        .filter(assignments::containsKey)
                                        .forEach(
                                                dependency -> {
                                                    indegree.compute(
                                                            assignment.nodeId(),
                                                            (ignored, value) -> value + 1);
                                                    dependents
                                                            .get(dependency)
                                                            .add(assignment.nodeId());
                                                }));
        var ready = new ArrayDeque<String>();
        indegree.forEach(
                (id, value) -> {
                    if (value == 0) ready.addLast(id);
                });
        var visited = 0;
        while (!ready.isEmpty()) {
            var current = ready.removeFirst();
            visited++;
            for (var dependent : dependents.get(current)) {
                var remaining = indegree.compute(dependent, (ignored, value) -> value - 1);
                if (remaining == 0) ready.addLast(dependent);
            }
        }
        if (visited != assignments.size()) {
            throw new IllegalArgumentException("协调计划的真实依赖 DAG 存在环");
        }
    }

    public record ExecutorAssignment(
            String nodeId,
            String description,
            Set<String> dependsOn,
            Map<String, InputBinding> inputBindings,
            String roleKey,
            String skillKey,
            TaskModelSelection modelSelection,
            int maxAttempts) {
        public ExecutorAssignment {
            nodeId = requireText(nodeId, "nodeId");
            description = requireText(description, "description");
            var dependencyIds = Set.copyOf(Objects.requireNonNull(dependsOn, "dependsOn 不能为空"));
            dependsOn = dependencyIds;
            inputBindings = Map.copyOf(Objects.requireNonNull(inputBindings, "inputBindings 不能为空"));
            if (inputBindings.keySet().stream().anyMatch(key -> key == null || key.isBlank())) {
                throw new IllegalArgumentException("inputBindings 名称不能为空白");
            }
            if (inputBindings.values().stream()
                    .anyMatch(binding -> !dependencyIds.contains(binding.sourceTaskNodeId()))) {
                throw new IllegalArgumentException("inputBindings 只能引用已声明依赖");
            }
            roleKey = requireText(roleKey, "roleKey");
            skillKey = requireText(skillKey, "skillKey");
            Objects.requireNonNull(modelSelection, "modelSelection 不能为空");
            if (maxAttempts < 1 || maxAttempts > 3) {
                throw new IllegalArgumentException("maxAttempts 必须在 1..3");
            }
        }
    }

    public record InputBinding(String sourceTaskNodeId) {
        public InputBinding {
            sourceTaskNodeId = requireText(sourceTaskNodeId, "sourceTaskNodeId");
        }
    }

    public record AggregationContract(Kind kind, List<String> executorOrder, String separator) {
        public AggregationContract {
            Objects.requireNonNull(kind, "aggregation kind 不能为空");
            executorOrder =
                    List.copyOf(Objects.requireNonNull(executorOrder, "executorOrder 不能为空"));
            if (executorOrder.isEmpty()
                    || executorOrder.stream().anyMatch(id -> id == null || id.isBlank())) {
                throw new IllegalArgumentException("executorOrder 不能为空且不能包含空标识");
            }
            separator = separator == null ? "" : separator;
            if (separator.length() > 32) {
                throw new IllegalArgumentException("聚合分隔符长度不能超过 32");
            }
        }

        public static AggregationContract passThrough(String executorId) {
            return new AggregationContract(Kind.PASS_THROUGH, List.of(executorId), "");
        }

        public static AggregationContract orderedConcat(
                List<String> executorOrder, String separator) {
            return new AggregationContract(Kind.ORDERED_CONCAT, executorOrder, separator);
        }

        void validate(Set<String> executorIds) {
            var orderedIds = Set.copyOf(executorOrder);
            if (orderedIds.size() != executorOrder.size() || !orderedIds.equals(executorIds)) {
                throw new IllegalArgumentException("AggregationContract 必须且只能覆盖全部结果执行者");
            }
            if (kind == Kind.PASS_THROUGH && executorOrder.size() != 1) {
                throw new IllegalArgumentException("PASS_THROUGH 聚合只允许一个执行者");
            }
        }

        public enum Kind {
            PASS_THROUGH,
            ORDERED_CONCAT,
            AGGREGATOR_REDUCE
        }
    }

    /** 静态 DAG 上的有界迭代组；循环状态独立持久化，不通过依赖回边表达。 */
    public record IterationGroup(
            String groupId,
            List<String> memberTaskNodeIds,
            String evaluatorTaskNodeId,
            int maxIterations) {
        public IterationGroup {
            groupId = requireText(groupId, "groupId");
            memberTaskNodeIds =
                    List.copyOf(
                            Objects.requireNonNull(memberTaskNodeIds, "memberTaskNodeIds 不能为空"));
            evaluatorTaskNodeId = requireText(evaluatorTaskNodeId, "evaluatorTaskNodeId");
            if (memberTaskNodeIds.isEmpty()
                    || memberTaskNodeIds.stream().anyMatch(id -> id == null || id.isBlank())
                    || memberTaskNodeIds.stream().distinct().count() != memberTaskNodeIds.size()) {
                throw new IllegalArgumentException("iteration members 必须非空且唯一");
            }
            if (memberTaskNodeIds.contains(evaluatorTaskNodeId)) {
                throw new IllegalArgumentException("evaluator 不能同时是 iteration member");
            }
            if (maxIterations < 1 || maxIterations > 8) {
                throw new IllegalArgumentException("maxIterations 必须在 1..8");
            }
        }

        private void validate(Map<String, ExecutorAssignment> assignments) {
            if (!assignments.keySet().containsAll(memberTaskNodeIds)
                    || !assignments.containsKey(evaluatorTaskNodeId)) {
                throw new IllegalArgumentException("IterationGroup 只能引用当前计划内子任务");
            }
            var evaluator = assignments.get(evaluatorTaskNodeId);
            if (!evaluator.dependsOn().containsAll(memberTaskNodeIds)) {
                throw new IllegalArgumentException("evaluator 必须依赖全部 iteration members");
            }
            var iterationIds = new java.util.HashSet<>(memberTaskNodeIds);
            iterationIds.add(evaluatorTaskNodeId);
            memberTaskNodeIds.forEach(
                    memberId -> {
                        var member = assignments.get(memberId);
                        if (member.dependsOn().contains(evaluatorTaskNodeId)) {
                            throw new IllegalArgumentException("iteration member 禁止依赖 evaluator");
                        }
                        var invalidDependency =
                                member.dependsOn().stream()
                                        .filter(dependency -> !"coordinator".equals(dependency))
                                        .filter(
                                                dependency ->
                                                        !memberTaskNodeIds.contains(dependency))
                                        .findFirst();
                        if (invalidDependency.isPresent()) {
                            throw new IllegalArgumentException(
                                    "iteration member 只能依赖 coordinator 或同组 member");
                        }
                    });
            assignments.values().stream()
                    .filter(assignment -> !iterationIds.contains(assignment.nodeId()))
                    .filter(
                            assignment ->
                                    assignment.dependsOn().stream()
                                            .anyMatch(memberTaskNodeIds::contains))
                    .filter(assignment -> !assignment.dependsOn().contains(evaluatorTaskNodeId))
                    .findFirst()
                    .ifPresent(
                            assignment -> {
                                throw new IllegalArgumentException(
                                        "依赖 iteration member 的下游任务必须同时依赖 evaluator");
                            });
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return value.trim();
    }
}
