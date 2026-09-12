package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskPlan.Status;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskPlan.TaskNode;

/** TaskPlan 的纯领域 DAG 计算；不领取 lease，不写数据库。 */
public final class TaskDagService {

    public List<PlanValidationFailure> validate(TaskPlan plan) {
        var failures = new ArrayList<PlanValidationFailure>();
        if (plan.maxParallelism() < 1 || plan.maxParallelism() > 64) {
            failures.add(
                    new PlanValidationFailure(
                            "INVALID_PARALLELISM", null, null, "maxParallelism 必须在 1..64"));
        }
        var nodes = plan.nodes();
        for (var node : nodes.values()) {
            for (var predecessor : node.dependsOn()) {
                if (node.nodeId().equals(predecessor)) {
                    failures.add(
                            new PlanValidationFailure(
                                    "SELF_DEPENDENCY", node.nodeId(), predecessor, "节点不能依赖自身"));
                } else if (!nodes.containsKey(predecessor)) {
                    failures.add(
                            new PlanValidationFailure(
                                    "MISSING_PREDECESSOR", node.nodeId(), predecessor, "依赖引用不存在"));
                }
            }
            node.inputBindings().values().stream()
                    .filter(binding -> !node.dependsOn().contains(binding.sourceTaskNodeId()))
                    .forEach(
                            binding ->
                                    failures.add(
                                            new PlanValidationFailure(
                                                    "INVALID_INPUT_BINDING",
                                                    node.nodeId(),
                                                    binding.sourceTaskNodeId(),
                                                    "输入绑定必须引用已声明前驱")));
        }
        var terminal = plan.goal().aggregationContract().executorOrder();
        terminal.stream()
                .filter(nodeId -> !nodes.containsKey(nodeId))
                .forEach(
                        nodeId ->
                                failures.add(
                                        new PlanValidationFailure(
                                                "MISSING_RESULT_NODE",
                                                nodeId,
                                                null,
                                                "聚合合同引用不存在的节点")));
        if (containsCycle(nodes)) {
            failures.add(new PlanValidationFailure("DAG_CYCLE", null, null, "TaskPlan 必须是无环图"));
        }
        return List.copyOf(failures);
    }

    /** 只计算依赖就绪候选；并行名额在 dispatch claim 事务锁 plan 后判定。 */
    public List<TaskNode> ready(TaskPlan plan) {
        if (plan.planStatus() != TaskPlan.PlanStatus.FROZEN
                || (plan.goal().iteration() != null
                        && plan.goal().iteration().stopReason() != null)) {
            return List.of();
        }
        return plan.nodes().values().stream()
                .filter(TaskNode::readyForClaim)
                .filter(node -> allowedByIteration(plan, node))
                .filter(
                        node ->
                                node.dependsOn().stream()
                                        .allMatch(
                                                predecessor ->
                                                        plan.nodes().get(predecessor).status()
                                                                == Status.COMPLETED))
                .sorted(java.util.Comparator.comparing(TaskNode::nodeId))
                .toList();
    }

    private static boolean allowedByIteration(TaskPlan plan, TaskNode node) {
        var iteration = plan.goal().iteration();
        if (iteration == null
                || (iteration.lastEvaluation() != null
                        && iteration.lastEvaluation().decision()
                                == IterationEvaluation.Decision.COMPLETE)) {
            return true;
        }
        return iteration.group().memberTaskNodeIds().contains(node.nodeId())
                || iteration.group().evaluatorTaskNodeId().equals(node.nodeId());
    }

    private static boolean containsCycle(Map<String, TaskNode> nodes) {
        var indegree = new HashMap<String, Integer>();
        var successors = new HashMap<String, Set<String>>();
        nodes.keySet().forEach(nodeId -> indegree.put(nodeId, 0));
        for (var node : nodes.values()) {
            for (var predecessor : node.dependsOn()) {
                if (!nodes.containsKey(predecessor) || predecessor.equals(node.nodeId())) {
                    continue;
                }
                successors
                        .computeIfAbsent(predecessor, ignored -> new HashSet<>())
                        .add(node.nodeId());
                indegree.compute(node.nodeId(), (ignored, value) -> value == null ? 1 : value + 1);
            }
        }
        var queue = new ArrayDeque<String>();
        indegree.forEach(
                (nodeId, degree) -> {
                    if (degree == 0) {
                        queue.add(nodeId);
                    }
                });
        var visited = 0;
        while (!queue.isEmpty()) {
            var current = queue.removeFirst();
            visited++;
            for (var successor : successors.getOrDefault(current, Set.of())) {
                var next = indegree.computeIfPresent(successor, (ignored, value) -> value - 1);
                if (next != null && next == 0) {
                    queue.addLast(successor);
                }
            }
        }
        return visited != nodes.size();
    }
}
