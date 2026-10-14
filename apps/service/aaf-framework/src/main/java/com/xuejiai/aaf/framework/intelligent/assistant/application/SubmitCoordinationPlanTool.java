package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.agent.port.ContextAwareToolHandler;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocation;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocationResult;
import com.xuejiai.aaf.framework.intelligent.ai.chat.AiProperties;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DecompositionBudget;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Role;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskModelSelection;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskPlan;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskPlan.TaskNode;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskPlanDraft;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskPlanDraft.AggregationContract;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskPlanDraft.ExecutorAssignment;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskPlanDraft.InputBinding;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskPlanDraft.IterationGroup;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantDefinitionPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ExecutionProfileSnapshotPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskPlanPort;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 协调者 execution 内唯一允许调用的写工具：提交协调计划，替代此前"输出严格 JSON 文本 + {@code
 * TaskCommandService.decodeAndValidatePlan} 手工解析"的模式。
 *
 * <p>动机：手工解析依赖 {@code streamEvents} 收敛到的最终文本，模型必须自己保证输出是严格单一 JSON 对象；改为工具调用后， 输入结构由工具入参 schema
 * 强制约束（模型侧无法输出非法结构），且工具调用本身产生 {@code TOOL_CALL_START}/{@code TOOL_RESULT_END} 事件，不像"改用结构化输出 {@code
 * call(...)}"那样需要放弃协调者 execution 的完整事件流投影与 Token 计量（核实 core {@code ReActAgent}
 * 源码确认原生结构化输出与合成降级路径均硬编码绑定在 {@code call(...)} 内部私有实现，无法与 {@code streamEvents} 组合）。
 *
 * <p>业务规则从 {@code decodeAndValidatePlan} 原样迁移，通过 {@link TaskPlanPort#find} 反查协调者节点自身持有的 {@code
 * roleKey}/{@code skillKey}/{@code modelSelection} 作为授权衰减与模型策略一致性的比对基准——这些字段已随 {@link
 * TaskPlan.TaskNode} 持久化，工具执行时可独立反查，不需要调用方额外传递。
 */
@Slf4j
public final class SubmitCoordinationPlanTool implements ContextAwareToolHandler {

    public static final String TOOL_NAME = "delegate_agents";

    private final TaskPlanPort boards;
    private final DecompositionBudget decompositionBudget;
    private final ExecutionProfileSnapshotPort profiles;
    private final AiProperties aiProperties;
    private final AssistantDefinitionPort definitions;

    public SubmitCoordinationPlanTool(
            TaskPlanPort boards,
            DecompositionBudget decompositionBudget,
            ExecutionProfileSnapshotPort profiles,
            AiProperties aiProperties,
            AssistantDefinitionPort definitions) {
        this.boards = Objects.requireNonNull(boards, "boards 不能为空");
        this.decompositionBudget =
                Objects.requireNonNull(decompositionBudget, "decompositionBudget 不能为空");
        this.profiles = Objects.requireNonNull(profiles, "profiles 不能为空");
        this.aiProperties = Objects.requireNonNull(aiProperties, "aiProperties 不能为空");
        this.definitions = Objects.requireNonNull(definitions, "definitions 不能为空");
    }

    @Override
    public String toolName() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return "提交本次协调产出的执行计划：目标、并行度、聚合方式、执行者列表，可选迭代组。" + "提交后立即生效冻结子任务，如需调整必须由协调者重新触发一次新的协调。";
    }

    /**
     * 等效只读：产出的是协调计划草稿（派生子任务定义），不直接执行业务动作——风险已在协调者派发子节点时的既有审批点 与各子任务自己的工具授权链路覆盖，与 {@link
     * SubmitExecutorPlanTool} 同一判断依据（ADR-006）。
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
                                "聚合方式。AGGREGATOR_REDUCE 下 requirements 必填，默认上限 8000 字符，超限会被拒绝并返回实际上限",
                                "properties",
                                Map.of(
                                        "kind",
                                        Map.of(
                                                "type",
                                                "string",
                                                "enum",
                                                List.of(
                                                        "PASS_THROUGH",
                                                        "ORDERED_CONCAT",
                                                        "AGGREGATOR_REDUCE"),
                                                "description",
                                                "聚合方式"),
                                        "executorOrder",
                                        Map.of(
                                                "type",
                                                "array",
                                                "items",
                                                Map.of("type", "string"),
                                                "description",
                                                "参与聚合的执行者 nodeId，必须且只能覆盖全部结果执行者"),
                                        "separator",
                                        Map.of(
                                                "type",
                                                "string",
                                                "description",
                                                "ORDERED_CONCAT 的拼接分隔符，最长 32 字符"),
                                        "requirements",
                                        Map.of(
                                                "type",
                                                "string",
                                                "description",
                                                "写给聚合者的聚合要求，应包含：用户真正要什么、各执行者结果如何综合、"
                                                        + "输出风格与格式篇幅约束、需要核对的事项；不要代写结果")),
                                "required",
                                List.of("kind", "executorOrder")),
                        "executors",
                        Map.of(
                                "type",
                                "array",
                                "description",
                                "执行者列表。roleKey/skillKey 可选，省略即与协调者相同；指定其它值必须属于 Assistant "
                                        + "已发布的候选目录且不扩大授权，越权会被拒绝并返回候选目录",
                                "items",
                                Map.of(
                                        "type",
                                        "object",
                                        "properties",
                                        Map.of(
                                                "nodeId",
                                                Map.of(
                                                        "type",
                                                        "string",
                                                        "description",
                                                        "执行者节点标识，计划内唯一"),
                                                "description",
                                                Map.of(
                                                        "type",
                                                        "string",
                                                        "description",
                                                        "该执行者的子任务描述"),
                                                "modelMode",
                                                Map.of(
                                                        "type",
                                                        "string",
                                                        "enum",
                                                        List.of("AUTO", "EXPLICIT"),
                                                        "description",
                                                        "必须与用户冻结的模型策略一致"),
                                                "roleKey",
                                                Map.of(
                                                        "type",
                                                        "string",
                                                        "description",
                                                        "可选，默认继承协调者"),
                                                "skillKey",
                                                Map.of(
                                                        "type",
                                                        "string",
                                                        "description",
                                                        "可选，默认继承协调者"),
                                                "dependsOn",
                                                Map.of(
                                                        "type",
                                                        "array",
                                                        "items",
                                                        Map.of("type", "string"),
                                                        "description",
                                                        "依赖的前序节点 nodeId；缺省依赖协调者"),
                                                "inputBindings",
                                                Map.of(
                                                        "type",
                                                        "object",
                                                        "description",
                                                        "输入名到前序结果的绑定，值为 {sourceTaskNodeId}，只能引用已声明依赖",
                                                        "additionalProperties",
                                                        Map.of(
                                                                "type",
                                                                "object",
                                                                "properties",
                                                                Map.of(
                                                                        "sourceTaskNodeId",
                                                                        Map.of("type", "string")),
                                                                "required",
                                                                List.of("sourceTaskNodeId"))),
                                                "maxAttempts",
                                                Map.of(
                                                        "type",
                                                        "integer",
                                                        "description",
                                                        "最大尝试次数，1..3，缺省 3")),
                                        "required",
                                        List.of("nodeId", "description", "modelMode"))),
                        "iterationGroup",
                        Map.of(
                                "type",
                                "object",
                                "description",
                                "可选迭代组配置",
                                "properties",
                                Map.of(
                                        "groupId",
                                        Map.of("type", "string"),
                                        "memberTaskNodeIds",
                                        Map.of("type", "array", "items", Map.of("type", "string")),
                                        "evaluatorTaskNodeId",
                                        Map.of("type", "string"),
                                        "maxIterations",
                                        Map.of("type", "integer", "description", "1..5，缺省 1")),
                                "required",
                                List.of("groupId", "memberTaskNodeIds", "evaluatorTaskNodeId"))),
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
            throw new IllegalStateException("delegate_agents 只能在编排板上的 COORDINATOR 节点内调用");
        }
        var board =
                boards.find(context.tenantId(), context.taskId())
                        .orElseThrow(() -> new IllegalStateException("当前任务没有可提交协调计划的任务板"));
        var coordinatorNode = board.nodes().get(nodeIdentity.nodeId());
        if (coordinatorNode == null || coordinatorNode.kind() != TaskNode.Kind.COORDINATOR) {
            throw new IllegalStateException("delegate_agents 只能由 COORDINATOR 节点调用");
        }

        var arguments = invocation.arguments();
        var teamTargets =
                board.nodes().values().stream()
                        .filter(subTask -> subTask.kind() == TaskNode.Kind.EXECUTOR)
                        .filter(subTask -> subTask.assistantTarget() != null)
                        .collect(
                                java.util.stream.Collectors.toUnmodifiableMap(
                                        TaskNode::nodeId, TaskNode::assistantTarget));
        var teamBoard = coordinatorNode.assistantTarget() != null && !teamTargets.isEmpty();

        // 授权衰减基准是委派方自身：协调者子任务上已冻结的 Role/Skill。executor 默认等于该基准；指定其它 Role/Skill
        // 时由 requireDelegable 校验候选目录与"不扩大授权"，不得放大到基准之外。
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
            var nodeId = requireStringField(node, "nodeId");
            final String roleKey;
            final String skillKey;
            if (teamBoard) {
                roleKey = requireStringField(node, "roleKey");
                skillKey = requireStringField(node, "skillKey");
                var target = teamTargets.get(nodeId);
                if (target == null
                        || !target.roleKey().equals(roleKey)
                        || !target.skillKey().equals(skillKey)) {
                    throw new IllegalArgumentException("Team 协调计划不能改变或跳过冻结 Worker");
                }
            } else {
                // 省略 roleKey / skillKey 表示与协调者相同；指定其它值必须属于候选目录且不扩大授权
                roleKey = optionalText(node, "roleKey", baselineRoleKey);
                skillKey = optionalText(node, "skillKey", baselineSkillKey);
                requireDelegable(context, baselineRoleKey, baselineSkillKey, roleKey, skillKey);
            }
            var modelMode = requireStringField(node, "modelMode");
            var modelSelection = resolveModelSelection(coordinatorNode.modelSelection(), modelMode);
            var dependsOn = decodeStringSet(node.get("dependsOn"));
            if (dependsOn.isEmpty()) {
                dependsOn = Set.of("coordinator");
            }
            assignments.add(
                    new ExecutorAssignment(
                            nodeId,
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
            if (!plannedExecutorIds.add(assignment.nodeId())) {
                throw new IllegalArgumentException("协调计划不能重复声明同一 nodeId");
            }
        }
        if (teamBoard && !plannedExecutorIds.equals(teamTargets.keySet())) {
            throw new IllegalArgumentException("Team 协调计划必须且只能覆盖全部冻结 Worker");
        }

        var aggregation = decodeAggregationContract(arguments.get("aggregationContract"));
        requireAggregationRequirements(aggregation, requirementsLimit(context));
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
        board.nodes().values().stream()
                .filter(subTask -> subTask.kind() == TaskNode.Kind.EXECUTOR)
                .map(TaskNode::nodeId)
                .forEach(cumulativeExecutorIds::add);
        cumulativeExecutorIds.addAll(plannedExecutorIds);
        effectiveBudget.requireCumulativeWithin(cumulativeExecutorIds.size());

        var plan =
                new TaskPlanDraft(
                        requireStringField(arguments, "goal"),
                        maxParallelism,
                        aggregation,
                        assignments,
                        iterationGroup);

        boards.applyTaskPlanDraft(context.tenantId(), context.taskId(), plan, context.lease());

        var values = new LinkedHashMap<String, Object>();
        values.put("executorCount", assignments.size());
        values.put("maxParallelism", maxParallelism);
        return new ToolInvocationResult("协调计划已提交并生效，执行者子任务已冻结。", values);
    }

    private static String optionalText(Map<String, Object> map, String field, String fallback) {
        var value = map.get(field);
        if (value == null) {
            return requireField(fallback, field + " 缺省时协调者节点没有可继承的值");
        }
        return requireString(value, field);
    }

    /**
     * 执行者 Role/Skill 的委派校验：与协调者相同直接通过；不同则必须属于 Assistant 已发布的候选目录（该 Role 的 ON_DEMAND 技能），且不扩大授权——执行者
     * Role 的候选工具集必须是协调者 Role 候选工具集的子集。技能的有效工具再与所属 Role 的工具 白名单求交集，因此 Role
     * 工具集不扩大即保证技能不会带入基准之外的工具。无法读取 Assistant 定义时视为无法确认候选目录，拒绝。
     */
    private void requireDelegable(
            InvocationContext context,
            String baselineRoleKey,
            String baselineSkillKey,
            String roleKey,
            String skillKey) {
        if (baselineRoleKey.equals(roleKey) && Objects.equals(baselineSkillKey, skillKey)) {
            return;
        }
        var definition =
                definitions
                        .findById(context.tenantId(), context.assistantId())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "协调者不能更改已冻结的 Role 或 Skill：无法读取 Assistant 定义以确认候选目录"));
        final Role baseline;
        final Role role;
        try {
            baseline = definition.requireRole(baselineRoleKey);
            role = definition.requireRole(roleKey);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException(
                    "协调者不能更改已冻结的 Role 或 Skill：Role 不在候选目录，候选：" + catalog(definition), failure);
        }
        if (skillKey == null || !definition.candidateOnDemandSkillKeys(role).contains(skillKey)) {
            throw new IllegalArgumentException(
                    "协调者不能更改已冻结的 Role 或 Skill：Skill 不在 Role "
                            + roleKey
                            + " 的候选目录，候选："
                            + catalog(definition));
        }
        if (!definition
                .candidateToolKeys(baseline)
                .containsAll(definition.candidateToolKeys(role))) {
            throw new IllegalArgumentException(
                    "执行者 Role "
                            + roleKey
                            + " 的工具集超出协调者 Role "
                            + baselineRoleKey
                            + "，不允许扩大授权，候选："
                            + catalog(definition));
        }
    }

    /** 候选目录摘要：每个 Role 及其可委派的 ON_DEMAND 技能，写进校验错误供协调者重试。 */
    private static String catalog(AssistantDefinition definition) {
        return definition.roles().stream()
                .map(
                        role ->
                                role.key()
                                        + definition.candidateOnDemandSkillKeys(role).stream()
                                                .sorted()
                                                .toList())
                .collect(java.util.stream.Collectors.joining("；"));
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
                                                    "sourceTaskNodeId")));
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
        var requirements = map.get("requirements");
        if (requirements != null && !(requirements instanceof String)) {
            throw new IllegalArgumentException("aggregationContract.requirements 必须是字符串");
        }
        return new AggregationContract(
                kind,
                executorOrder,
                separator == null ? "" : separator.toString(),
                (String) requirements);
    }

    /** 聚合要求的生效上限：请求参数 &gt; 系统默认（{@code AiProperties}），都不得超过硬顶。请求值超过硬顶按硬顶处理并记日志， 防止客户端绕过保护。 */
    private int requirementsLimit(InvocationContext context) {
        var requested =
                profiles.find(context.tenantId(), context.executionId())
                        .map(profile -> profile.executionIntent().aggregationRequirementsMaxChars())
                        .orElse(null);
        var configured =
                requested != null ? requested : aiProperties.getAggregationRequirementsMaxChars();
        if (configured > AggregationContract.REQUIREMENTS_HARD_MAX_CHARS) {
            log.warn(
                    "[Assistant协调] 聚合要求上限 {} 超过硬顶，已按 {} 处理：executionId={}",
                    configured,
                    AggregationContract.REQUIREMENTS_HARD_MAX_CHARS,
                    context.executionId().value());
            return AggregationContract.REQUIREMENTS_HARD_MAX_CHARS;
        }
        return configured;
    }

    /**
     * AGGREGATOR_REDUCE 下聚合要求必填，所有聚合方式都不得超过上限。校验失败抛出带明确原因的错误，ReAct 循环收到后让协调者补充重试； JSON Schema 无法表达按
     * kind 条件必填，因此由代码而不是 {@code required} 数组校验。
     */
    private static void requireAggregationRequirements(AggregationContract aggregation, int limit) {
        var requirements = aggregation.requirements();
        if (aggregation.kind() == AggregationContract.Kind.AGGREGATOR_REDUCE
                && requirements.isBlank()) {
            throw new IllegalArgumentException(
                    "AGGREGATOR_REDUCE 必须提供 aggregationContract.requirements：写明用户真正要什么、"
                            + "各执行者结果如何综合、输出风格与格式篇幅约束、需要核对的事项");
        }
        if (requirements.length() > limit) {
            throw new IllegalArgumentException(
                    "aggregationContract.requirements 超过上限 "
                            + limit
                            + " 字符（当前 "
                            + requirements.length()
                            + "），请精简为对聚合者的指令，不要代写结果");
        }
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
                decodeStringList(map.get("memberTaskNodeIds")),
                requireStringField(map, "evaluatorTaskNodeId"),
                optionalPositiveInt(map, "maxIterations", 1));
    }

    @SuppressWarnings("unchecked")
    private static List<String> decodeStringList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            throw new IllegalArgumentException(
                    "期望字符串数组，实际: " + (value == null ? "null" : value.getClass()));
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
