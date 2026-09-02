package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.agent.port.ContextAwareToolHandler;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocation;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocationResult;
import com.xuejiai.aaf.framework.intelligent.assistant.model.CoordinationPlan;
import com.xuejiai.aaf.framework.intelligent.assistant.model.CoordinationPlan.AggregationContract;
import com.xuejiai.aaf.framework.intelligent.assistant.model.CoordinationPlan.ExecutorAssignment;
import com.xuejiai.aaf.framework.intelligent.assistant.model.CoordinationPlan.InputBinding;
import com.xuejiai.aaf.framework.intelligent.assistant.model.CoordinationPlan.IterationGroup;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DecompositionBudget;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskBoard;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskBoard.SubTask;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskModelSelection;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskBoardPort;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 协调者 execution 内唯一允许调用的写工具：提交协调计划，替代此前"输出严格 JSON 文本 + {@code
 * DelegatedTaskCoordinator.decodeAndValidatePlan} 手工解析"的模式。
 *
 * <p>动机：手工解析依赖 {@code streamEvents} 收敛到的最终文本，模型必须自己保证输出是严格单一 JSON 对象；改为工具调用后，
 * 输入结构由工具入参 schema 强制约束（模型侧无法输出非法结构），且工具调用本身产生 {@code TOOL_CALL_START}/{@code
 * TOOL_RESULT_END} 事件，不像"改用结构化输出 {@code call(...)}"那样需要放弃协调者 execution 的完整事件流投影与 Token
 * 计量（核实 core {@code ReActAgent} 源码确认原生结构化输出与合成降级路径均硬编码绑定在 {@code call(...)} 内部私有实现，无法与
 * {@code streamEvents} 组合）。
 *
 * <p>业务规则从 {@code decodeAndValidatePlan} 原样迁移，通过 {@link TaskBoardPort#find} 反查协调者节点自身持有的
 * {@code roleKey}/{@code skillKey}/{@code modelSelection} 作为授权衰减与模型策略一致性的比对基准——这些字段已随
 * {@link TaskBoard.SubTask} 持久化，工具执行时可独立反查，不需要调用方额外传递。
 */
public final class SubmitCoordinationPlanTool implements ContextAwareToolHandler {

    public static final String TOOL_NAME = "submit_coordination_plan";

    private final TaskBoardPort boards;
    private final DecompositionBudget decompositionBudget;

    public SubmitCoordinationPlanTool(TaskBoardPort boards, DecompositionBudget decompositionBudget) {
        this.boards = Objects.requireNonNull(boards, "boards 不能为空");
        this.decompositionBudget =
                Objects.requireNonNull(decompositionBudget, "decompositionBudget 不能为空");
    }

    @Override
    public String toolName() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return "提交本次协调产出的执行计划：目标、并行度、聚合方式、执行者列表，可选迭代组。"
                + "提交后立即生效冻结子任务，如需调整必须由协调者重新触发一次新的协调。";
    }

    /**
     * 等效只读：产出的是协调计划草稿（派生子任务定义），不直接执行业务动作——风险已在协调者派发子节点时的既有审批点
     * 与各子任务自己的工具授权链路覆盖，与 {@link SubmitExecutorPlanTool} 同一判断依据（ADR-006）。
     */
    @Override
    public boolean readOnly() {
        return true;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type",
                "object",
                "properties",
                Map.of(
                        "goal",
                        Map.of("type", "string", "description", "本次协调的目标概述"),
                        "maxParallelism",
                        Map.of("type", "integer", "description", "最大并行执行者数量"),
                        "aggregationContract",
                        Map.of(
                                "type",
                                "object",
                                "description",
                                "聚合方式：kind（PASS_THROUGH/ORDERED_CONCAT 等）与相关配置"),
                        "executors",
                        Map.of(
                                "type",
                                "array",
                                "description",
                                "执行者列表，每项含 subTaskId/description/roleKey/skillKey/modelMode",
                                "items",
                                Map.of("type", "object")),
                        "iterationGroup",
                        Map.of("type", "object", "description", "可选迭代组配置")),
                "required",
                List.of("goal", "aggregationContract", "executors"));
    }

    @Override
    public Mono<ToolInvocationResult> invoke(ToolInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation 不能为空");
        return Mono.fromCallable(() -> submit(invocation)).subscribeOn(Schedulers.boundedElastic());
    }

    private ToolInvocationResult submit(ToolInvocation invocation) {
        var context = invocation.context();
        var nodeIdentity = context.nodeIdentity();
        if (nodeIdentity == null) {
            throw new IllegalStateException("submit_coordination_plan 只能在编排板上的 COORDINATOR 节点内调用");
        }
        var board =
                boards.find(context.tenantId(), context.taskId())
                        .orElseThrow(() -> new IllegalStateException("当前任务没有可提交协调计划的任务板"));
        var coordinatorNode = board.subTasks().get(nodeIdentity.subTaskId());
        if (coordinatorNode == null || coordinatorNode.kind() != SubTask.Kind.COORDINATOR) {
            throw new IllegalStateException("submit_coordination_plan 只能由 COORDINATOR 节点调用");
        }

        var arguments = invocation.arguments();
        var teamTargets =
                board.subTasks().values().stream()
                        .filter(subTask -> subTask.kind() == SubTask.Kind.EXECUTOR)
                        .filter(subTask -> subTask.assistantTarget() != null)
                        .collect(
                                java.util.stream.Collectors.toUnmodifiableMap(
                                        SubTask::subTaskId, SubTask::assistantTarget));
        var teamBoard = coordinatorNode.assistantTarget() != null && !teamTargets.isEmpty();

        // 授权衰减基准是委派方自身：协调者子任务上已冻结的 Role/Skill。executor 只能等于该基准，不得放大到基准之外。
        final String baselineRoleKey;
        final String baselineSkillKey;
        if (teamBoard) {
            baselineRoleKey = null;
            baselineSkillKey = null;
        } else {
            baselineRoleKey = requireField(coordinatorNode.roleKey(), "协调者节点缺少已冻结 Role");
            baselineSkillKey = coordinatorNode.skillKey();
        }

        var rawExecutors = decodeExecutorList(arguments.get("executors"));
        if (rawExecutors.isEmpty() || rawExecutors.size() > DecompositionBudget.HARD_LIMIT) {
            throw new IllegalArgumentException(
                    "协调计划必须包含 1.." + DecompositionBudget.HARD_LIMIT + " 个执行者");
        }

        var assignments = new ArrayList<ExecutorAssignment>();
        for (var node : rawExecutors) {
            var subTaskId = requireStringField(node, "subTaskId");
            var roleKey = requireStringField(node, "roleKey");
            var skillKey = requireStringField(node, "skillKey");
            if (teamBoard) {
                var target = teamTargets.get(subTaskId);
                if (target == null
                        || !target.roleKey().equals(roleKey)
                        || !target.skillKey().equals(skillKey)) {
                    throw new IllegalArgumentException("Team 协调计划不能改变或跳过冻结 Worker");
                }
            } else if (!baselineRoleKey.equals(roleKey)
                    || !Objects.equals(baselineSkillKey, skillKey)) {
                throw new IllegalArgumentException("协调者不能更改已冻结的 Role 或 Skill");
            }
            var modelMode = requireStringField(node, "modelMode");
            var modelSelection = resolveModelSelection(coordinatorNode.modelSelection(), modelMode);
            var dependsOn = decodeStringSet(node.get("dependsOn"));
            if (dependsOn.isEmpty()) {
                dependsOn = Set.of("coordinator");
            }
            assignments.add(
                    new ExecutorAssignment(
                            subTaskId,
                            requireStringField(node, "description"),
                            dependsOn,
                            decodeInputBindings(node.get("inputBindings")),
                            roleKey,
                            skillKey,
                            modelSelection,
                            optionalPositiveInt(node, "maxAttempts", 3)));
        }

        var plannedExecutorIds = new LinkedHashSet<String>();
        for (var assignment : assignments) {
            if (!plannedExecutorIds.add(assignment.subTaskId())) {
                throw new IllegalArgumentException("协调计划不能重复声明同一 subTaskId");
            }
        }
        if (teamBoard && !plannedExecutorIds.equals(teamTargets.keySet())) {
            throw new IllegalArgumentException("Team 协调计划必须且只能覆盖全部冻结 Worker");
        }

        var aggregation = decodeAggregationContract(arguments.get("aggregationContract"));
        var iterationGroup = decodeIterationGroup(arguments.get("iterationGroup"));
        var maxParallelism = optionalPositiveInt(arguments, "maxParallelism", 1);

        var effectiveBudget =
                teamBoard
                        ? decompositionBudget.effectiveForFixedTeam(teamTargets.size())
                        : decompositionBudget;
        effectiveBudget.requireWithin(
                assignments.size(),
                maxParallelism,
                iterationGroup == null ? 1 : iterationGroup.maxIterations());

        var cumulativeExecutorIds = new LinkedHashSet<String>();
        board.subTasks().values().stream()
                .filter(subTask -> subTask.kind() == SubTask.Kind.EXECUTOR)
                .map(SubTask::subTaskId)
                .forEach(cumulativeExecutorIds::add);
        cumulativeExecutorIds.addAll(plannedExecutorIds);
        effectiveBudget.requireCumulativeWithin(cumulativeExecutorIds.size());

        var plan =
                new CoordinationPlan(
                        requireStringField(arguments, "goal"),
                        maxParallelism,
                        aggregation,
                        assignments,
                        iterationGroup);

        boards.applyCoordinationPlan(context.tenantId(), context.taskId(), plan, context.lease());

        var values = new LinkedHashMap<String, Object>();
        values.put("executorCount", assignments.size());
        values.put("maxParallelism", maxParallelism);
        return new ToolInvocationResult("协调计划已提交并生效，执行者子任务已冻结。", values);
    }

    private static TaskModelSelection resolveModelSelection(
            TaskModelSelection frozenSelection, String modelMode) {
        if (frozenSelection.mode() == TaskModelSelection.Mode.AUTO
                && TaskModelSelection.Mode.AUTO.name().equals(modelMode)) {
            return TaskModelSelection.auto();
        }
        if (frozenSelection.mode() == TaskModelSelection.Mode.EXPLICIT
                && TaskModelSelection.Mode.EXPLICIT.name().equals(modelMode)) {
            return TaskModelSelection.explicit(frozenSelection.modelId());
        }
        throw new IllegalArgumentException("协调者不能改变用户冻结的模型策略");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> decodeExecutorList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            throw new IllegalArgumentException("executors 必须是数组");
        }
        var executors = new ArrayList<Map<String, Object>>();
        for (var entry : rawList) {
            if (!(entry instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("executors 的每一项必须是对象");
            }
            executors.add((Map<String, Object>) map);
        }
        return executors;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, InputBinding> decodeInputBindings(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> rawMap)) {
            throw new IllegalArgumentException("inputBindings 必须是对象");
        }
        var bindings = new LinkedHashMap<String, InputBinding>();
        ((Map<String, Object>) rawMap)
                .forEach(
                        (key, raw) -> {
                            if (!(raw instanceof Map<?, ?> bindingMap)) {
                                throw new IllegalArgumentException("inputBindings 的值必须是对象");
                            }
                            bindings.put(
                                    key,
                                    new InputBinding(
                                            requireStringField(
                                                    (Map<String, Object>) bindingMap,
                                                    "sourceSubTaskId")));
                        });
        return bindings;
    }

    private static AggregationContract decodeAggregationContract(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            throw new IllegalArgumentException("aggregationContract 必须是对象");
        }
        @SuppressWarnings("unchecked")
        var map = (Map<String, Object>) rawMap;
        var kind = AggregationContract.Kind.valueOf(requireStringField(map, "kind"));
        var executorOrder = decodeStringList(map.get("executorOrder"));
        var separator = map.get("separator");
        return new AggregationContract(
                kind, executorOrder, separator == null ? "" : separator.toString());
    }

    private static IterationGroup decodeIterationGroup(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Map<?, ?> rawMap)) {
            throw new IllegalArgumentException("iterationGroup 必须是对象");
        }
        @SuppressWarnings("unchecked")
        var map = (Map<String, Object>) rawMap;
        return new IterationGroup(
                requireStringField(map, "groupId"),
                decodeStringList(map.get("memberSubTaskIds")),
                requireStringField(map, "evaluatorSubTaskId"),
                optionalPositiveInt(map, "maxIterations", 1));
    }

    @SuppressWarnings("unchecked")
    private static List<String> decodeStringList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            throw new IllegalArgumentException("期望字符串数组，实际: " + (value == null ? "null" : value.getClass()));
        }
        return ((List<Object>) rawList).stream().map(item -> requireString(item, "数组元素")).toList();
    }

    private static Set<String> decodeStringSet(Object value) {
        if (value == null) {
            return Set.of();
        }
        return Set.copyOf(decodeStringList(value));
    }

    private static String requireStringField(Map<String, Object> map, String field) {
        return requireString(map.get(field), field);
    }

    private static String requireString(Object value, String field) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return text;
    }

    private static String requireField(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
        return value;
    }

    private static int optionalPositiveInt(Map<String, Object> map, String field, int fallback) {
        var value = map.get(field);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new IllegalArgumentException(field + " 必须是数字");
    }

}
