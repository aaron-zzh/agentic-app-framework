package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.agent.port.ContextAwareToolHandler;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocation;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocationResult;
import com.xuejiai.aaf.framework.intelligent.assistant.port.plan.ExecutorPlanPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.plan.ExecutorPlanPort.StepDraft;
import com.xuejiai.aaf.framework.intelligent.assistant.port.plan.ExecutorPlanPort.SubmitPlanCommand;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ExecutionEventStatus;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.OwnerType;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventPayload;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventStorePort;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.EventId;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 单次 execution 内可用的步骤计划提交工具（AAF-107 选项 B 架构改造，2026-09-02，替代原"只在独立只读 planning
 * execution 内可见"设计）。
 *
 * <p>不再区分"规划阶段"与"执行阶段"两次独立 execution——协调者或执行者节点在自己的一次完整能力 execution 里，
 * 判断需要按步骤推进时随时可以调用本工具。首次调用时若本节点还没有活跃计划，本工具自己先原子完成 {@code
 * beginPlanning}，紧接着 {@code submit} 与 {@code claimApproved}（提交即批准且立即进入
 * {@code EXECUTING}，不再有独立 {@code APPROVED} 等待认领中间态——提交与执行在同一次 execution 内连续发生，
 * 没有"等待另一次调用认领"的时间差），模型只感知到调了一次工具。
 *
 * <p>只暴露 {@code goal}、{@code risks}、{@code verification}、{@code steps} 四个入参；{@code planId} 与 {@code
 * expectedLockVersion} 由本工具通过 {@code ExecutorPlanPort.findActive} 按 {@code (tenantId, taskId)}
 * 现查现用，不接受模型传入——防止模型跨计划提交或伪造版本号绕过 CAS。查出的活跃计划还必须归属调用者自己的板节点（{@code
 * nodeIdentity.subTaskId()}），杜绝跨节点提交。
 *
 * <p><b>不设独立审批关卡（ADR-006「决策推翻」2026-09-01）</b>：对齐官方 {@code
 * permission-system.html} 后确认，权限系统按具体工具调用分级（ALLOW/DENY/ASK），没有"计划本身要不要审"这一层概念。
 * "提交步骤列表"这个动作不新增执行面、不引入新的 Agent 身份，风险已在两处覆盖：协调者产出 {@code CoordinationPlan}
 * 派生新节点时的既有审批点、步骤执行阶段具体工具调用前的 {@code DefaultToolGateway}/{@code AuthorizationGrant}
 * 授权链路。因此本工具提交后固定转 {@code APPROVED} 再转 {@code EXECUTING}，不判定白名单、不产生 {@code
 * REVIEW_REQUIRED}。
 */
public final class SubmitExecutorPlanTool implements ContextAwareToolHandler {

    public static final String TOOL_NAME = "submit_executor_plan";

    private final ExecutorPlanPort plans;
    private final ExecutionEventStorePort events;
    private final Clock clock;

    public SubmitExecutorPlanTool(
            ExecutorPlanPort plans, ExecutionEventStorePort events, Clock clock) {
        this.plans = Objects.requireNonNull(plans, "plans 不能为空");
        this.events = Objects.requireNonNull(events, "events 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    @Override
    public String toolName() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return "提交本次规划产出的执行计划：目标、风险自评、验证方式与有序步骤列表。提交后不可再修改正文，"
                + "如需调整必须由协调者重新触发一次新的规划。";
    }

    /**
     * 等效只读（ADR-006「决策推翻」）：产出的是计划草稿而非业务动作，不新增执行面、不引入新的 Agent 身份，
     * 风险已由协调者派发子节点时的既有审批点与步骤执行阶段的工具授权链路覆盖。对齐官方 {@code ActionEffect.GENERATED_CONTENT}
     * 语义，在 {@code ControlMode.READ_ONLY} 下允许调用。
     */
    @Override
    public boolean readOnly() {
        return true;
    }

    @Override
    public Map<String, Object> inputSchema() {
        var stepProperties =
                Map.ofEntries(
                        Map.entry("stepKey", Map.of("type", "string")),
                        Map.entry("title", Map.of("type", "string")),
                        Map.entry("instruction", Map.of("type", "string")),
                        Map.entry(
                                "dependencies",
                                Map.of("type", "array", "items", Map.of("type", "string"))),
                        Map.entry(
                                "requiredTools",
                                Map.of("type", "array", "items", Map.of("type", "string"))),
                        Map.entry(
                                "completionCriteria",
                                Map.of("type", "array", "items", Map.of("type", "string"))));
        var stepItemSchema =
                Map.of(
                        "type",
                        "object",
                        "properties",
                        stepProperties,
                        "required",
                        List.of("stepKey", "title", "instruction"));
        var stepsSchema =
                Map.of(
                        "type",
                        "array",
                        "description",
                        "有序步骤列表",
                        "items",
                        stepItemSchema);
        var properties =
                Map.of(
                        "goal",
                        Map.of("type", "string", "description", "本次执行的目标概述"),
                        "risks",
                        Map.of(
                                "type",
                                "array",
                                "items",
                                Map.of("type", "string"),
                                "description",
                                "风险自评列表，仅供展示与审计，不作为门控输入"),
                        "verification",
                        Map.of("type", "object", "description", "验证方式的自由结构说明"),
                        "steps",
                        stepsSchema);
        return Map.of("type", "object", "properties", properties, "required", List.of("goal", "steps"));
    }

    @Override
    public Mono<ToolInvocationResult> invoke(ToolInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation 不能为空");
        return Mono.fromCallable(() -> submit(invocation))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(
                        outcome ->
                                events.append(outcome.event(), invocation.context().lease())
                                        .thenReturn(outcome.result()));
    }

    private SubmitOutcome submit(ToolInvocation invocation) {
        var context = invocation.context();
        var nodeIdentity = context.nodeIdentity();
        if (nodeIdentity == null) {
            throw new IllegalStateException("submit_executor_plan 只能在编排板上的节点内调用");
        }
        var goal = requireArgument(invocation.arguments(), "goal");
        var active =
                plans.findActive(context.tenantId(), context.taskId(), nodeIdentity.subTaskId())
                        .orElseGet(
                                () ->
                                        plans.beginPlanning(
                                                new ExecutorPlanPort.BeginPlanningCommand(
                                                        context.tenantId(),
                                                        context.taskId(),
                                                        nodeIdentity.subTaskId(),
                                                        nodeIdentity.subTaskId(),
                                                        goal,
                                                        Map.of(),
                                                        clock.instant())));
        if (!active.boardId().equals(nodeIdentity.subTaskId())) {
            throw new IllegalStateException("当前活跃计划不属于本节点，禁止跨节点提交");
        }
        var steps = decodeSteps(invocation.arguments());
        var risks = decodeStringList(invocation.arguments().get("risks"));
        var verification = decodeVerification(invocation.arguments().get("verification"));
        var command =
                new SubmitPlanCommand(
                        context.tenantId(),
                        active.planId(),
                        active.lockVersion(),
                        risks,
                        verification,
                        steps,
                        "AI",
                        nodeIdentity.subTaskId(),
                        true,
                        clock.instant());
        var plan = plans.submit(command);
        // 提交即批准且立即进入执行（AAF-107 选项 B 架构改造，2026-09-02）：不再有独立 APPROVED 等待认领
        // 中间态——提交与执行发生在同一次 execution 内，没有"等待另一次调用认领"的时间差，
        // claimApproved 直接在这里原子完成，模型只感知到调了一次工具。
        var executing =
                plans.claimApproved(
                        new ExecutorPlanPort.ClaimApprovedCommand(
                                context.tenantId(), plan.planId(), plan.lockVersion(), clock.instant()));
        var values = new LinkedHashMap<String, Object>();
        values.put("planId", executing.planId());
        values.put("status", executing.status().name());
        values.put("stepCount", steps.size());
        var result = new ToolInvocationResult("计划已提交并批准，可以开始执行。", values);
        var event =
                new ExecutionEvent(
                        new EventId("executor-plan-submitted-" + invocation.toolCallId()),
                        context.tenantId(),
                        context.conversationId(),
                        context.sessionId(),
                        context.taskId(),
                        context.executionId(),
                        context.runId(),
                        context.parentExecutionId(),
                        0,
                        ExecutionEventType.EXECUTOR_PLAN_SUBMITTED,
                        ExecutionEventStatus.RUNNING,
                        context.controlMode(),
                        OwnerType.AGENT,
                        context.assistantId(),
                        null,
                        context.userId(),
                        context.correlationId(),
                        context.causationId(),
                        context.idempotencyKey(),
                        new ExecutionEventPayload(values),
                        clock.instant(),
                        context.nodeIdentity());
        return new SubmitOutcome(result, event);
    }

    @SuppressWarnings("unchecked")
    private static List<StepDraft> decodeSteps(Map<String, Object> arguments) {
        var raw = arguments.get("steps");
        if (!(raw instanceof List<?> rawSteps) || rawSteps.isEmpty()) {
            throw new IllegalArgumentException("submit_executor_plan 的 steps 不能为空");
        }
        var drafts = new ArrayList<StepDraft>();
        var ordinal = 1;
        for (var entry : rawSteps) {
            if (!(entry instanceof Map<?, ?> stepMap)) {
                throw new IllegalArgumentException("steps 的每一项必须是对象");
            }
            var step = (Map<String, Object>) stepMap;
            drafts.add(
                    new StepDraft(
                            requireStepField(step, "stepKey"),
                            ordinal++,
                            requireStepField(step, "title"),
                            requireStepField(step, "instruction"),
                            decodeStringList(step.get("dependencies")),
                            decodeStringList(step.get("requiredTools")),
                            decodeStringList(step.get("completionCriteria"))));
        }
        return drafts;
    }

    private static String requireStepField(Map<String, Object> step, String field) {
        var value = step.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("步骤缺少必填字段: " + field);
        }
        return text;
    }

    private static String requireArgument(Map<String, Object> arguments, String field) {
        var value = arguments.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("submit_executor_plan 缺少必填字段: " + field);
        }
        return text;
    }

    @SuppressWarnings("unchecked")
    private static List<String> decodeStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> rawList)) {
            throw new IllegalArgumentException("期望字符串数组，实际: " + value.getClass());
        }
        var list = (List<Object>) rawList;
        return list.stream()
                .map(
                        item -> {
                            if (!(item instanceof String text) || text.isBlank()) {
                                throw new IllegalArgumentException("数组元素必须是非空字符串");
                            }
                            return text;
                        })
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> decodeVerification(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> rawMap)) {
            throw new IllegalArgumentException("verification 必须是对象");
        }
        return Map.copyOf((Map<String, Object>) rawMap);
    }

    private record SubmitOutcome(ToolInvocationResult result, ExecutionEvent event) {}
}
