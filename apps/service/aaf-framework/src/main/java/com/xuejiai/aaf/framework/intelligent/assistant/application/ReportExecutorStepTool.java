package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.agent.port.ContextAwareToolHandler;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocation;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocationResult;
import com.xuejiai.aaf.framework.intelligent.assistant.model.plan.ExecutorPlanStep;
import com.xuejiai.aaf.framework.intelligent.assistant.port.plan.ExecutorPlanPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.plan.ExecutorPlanPort.CompleteStepCommand;
import com.xuejiai.aaf.framework.intelligent.assistant.port.plan.ExecutorPlanPort.FailStepCommand;
import com.xuejiai.aaf.framework.intelligent.assistant.port.plan.ExecutorPlanPort.StartStepCommand;
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
 * 已批准执行阶段内唯一允许模型上报步骤边界的工具（AAF-107 #10705）。
 *
 * <p>{@code report_executor_step} 让模型在单次 execution 内自由推进已提交计划的全部步骤，AAF 侧不介入模型推理过程， 因此 {@code
 * ExecutorPlanStep.Status} 要有真实数据，必须由模型自己显式上报步骤边界——复用 {@link SubmitExecutorPlanTool}
 * 已确立的"模型主动上报"模式，而非从底层工具调用事件流反推（一个 step 可能对应 0~N 次工具调用， 无法可靠映射回步骤边界）。
 *
 * <p>{@code planId}/{@code expectedLockVersion} 由本工具按 {@code (tenantId, taskId, boardId)}
 * 现查现用，不接受模型 传入，理由与 {@link SubmitExecutorPlanTool} 相同：防止模型跨计划上报或伪造版本号绕过 CAS。
 *
 * <p>事件直接由本工具构造并 {@code append}（复用 {@link SupportHandoffTool} 已确立的"工具直接注入 {@link
 * ExecutionEventStorePort} 写事件"模式），不经过 {@code TaskTransition}——{@code ExecutorPlan} 是独立聚合根，有自己的
 * {@code lock_version}，不属于 {@code AssistantTask}/{@code TaskBoard} 级别的状态转换机制管辖范围。
 */
public final class ReportExecutorStepTool implements ContextAwareToolHandler {

    public static final String TOOL_NAME = "report_executor_step";

    private final ExecutorPlanPort plans;
    private final ExecutionEventStorePort events;
    private final Clock clock;

    public ReportExecutorStepTool(
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
        return "上报当前已批准计划中某个步骤的进度：STARTED（开始执行）、COMPLETED（已完成）或 FAILED（执行失败）。"
                + "必须按计划声明的顺序与依赖关系逐条上报，不要跳过或提前上报未满足依赖的步骤。";
    }

    /** 纯审计记录，不产生业务副作用——状态机守卫（{@code startStep}/{@code completeStep}/{@code failStep}）本身即幂等边界。 */
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
                        "stepKey",
                        Map.of("type", "string", "description", "计划中声明的步骤标识"),
                        "outcome",
                        Map.of(
                                "type",
                                "string",
                                "enum",
                                List.of("STARTED", "COMPLETED", "FAILED"),
                                "description",
                                "步骤当前进度"),
                        "resultRef",
                        Map.of("type", "string", "description", "COMPLETED 时可选的结果引用"),
                        "failureCode",
                        Map.of("type", "string", "description", "FAILED 时必填的失败原因码")),
                "required",
                List.of("stepKey", "outcome"));
    }

    @Override
    public Mono<ToolInvocationResult> invoke(ToolInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation 不能为空");
        return Mono.fromCallable(() -> report(invocation))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(
                        outcome ->
                                events.append(outcome.event(), invocation.context().lease())
                                        .thenReturn(outcome.result()));
    }

    private ReportOutcome report(ToolInvocation invocation) {
        var context = invocation.context();
        var nodeIdentity = context.nodeIdentity();
        if (nodeIdentity == null) {
            throw new IllegalStateException("当前节点不在编排板上，无法上报步骤进度");
        }
        var active =
                plans.findActive(context.tenantId(), context.taskId(), nodeIdentity.subTaskId())
                        .orElseThrow(() -> new IllegalStateException("当前任务没有处于执行阶段的计划，无法上报步骤进度"));
        if (!active.boardId().equals(nodeIdentity.subTaskId())) {
            throw new IllegalStateException("当前活跃计划不属于本节点，禁止跨节点上报");
        }
        var stepKey = requireArgument(invocation.arguments(), "stepKey");
        var outcome = requireArgument(invocation.arguments(), "outcome");
        var at = clock.instant();
        return switch (outcome) {
            case "STARTED" -> reportStarted(context, invocation, active.planId(), stepKey, at);
            case "COMPLETED" -> reportCompleted(context, invocation, active.planId(), stepKey, at);
            case "FAILED" -> reportFailed(context, invocation, active.planId(), stepKey, at);
            default ->
                    throw new IllegalArgumentException(
                            "outcome 只能是 STARTED/COMPLETED/FAILED，实际: " + outcome);
        };
    }

    private ReportOutcome reportStarted(
            InvocationContext context,
            ToolInvocation invocation,
            String planId,
            String stepKey,
            Instant at) {
        var current = plans.findSteps(context.tenantId(), planId);
        var lockVersion = requireStep(current, stepKey).lockVersion();
        var step =
                plans.startStep(
                        new StartStepCommand(context.tenantId(), planId, stepKey, lockVersion, at));
        var values = stepValues(planId, step.stepKey(), "STARTED");
        var event =
                stepEvent(
                        context,
                        invocation,
                        ExecutionEventType.EXECUTOR_PLAN_STEP_STARTED,
                        ExecutionEventStatus.RUNNING,
                        values,
                        at);
        return new ReportOutcome(successResult("STARTED", step.stepKey()), event);
    }

    private ReportOutcome reportCompleted(
            InvocationContext context,
            ToolInvocation invocation,
            String planId,
            String stepKey,
            Instant at) {
        var current = plans.findSteps(context.tenantId(), planId);
        var lockVersion = requireStep(current, stepKey).lockVersion();
        var resultRef = optionalArgument(invocation.arguments(), "resultRef");
        var step =
                plans.completeStep(
                        new CompleteStepCommand(
                                context.tenantId(), planId, stepKey, lockVersion, resultRef, at));
        var values = stepValues(planId, step.stepKey(), "COMPLETED");
        var event =
                stepEvent(
                        context,
                        invocation,
                        ExecutionEventType.EXECUTOR_PLAN_STEP_COMPLETED,
                        ExecutionEventStatus.COMPLETED,
                        values,
                        at);
        return new ReportOutcome(successResult("COMPLETED", step.stepKey()), event);
    }

    private ReportOutcome reportFailed(
            InvocationContext context,
            ToolInvocation invocation,
            String planId,
            String stepKey,
            Instant at) {
        var current = plans.findSteps(context.tenantId(), planId);
        var lockVersion = requireStep(current, stepKey).lockVersion();
        var failureCode = requireArgument(invocation.arguments(), "failureCode");
        var step =
                plans.failStep(
                        new FailStepCommand(
                                context.tenantId(), planId, stepKey, lockVersion, failureCode, at));
        var values = stepValues(planId, step.stepKey(), "FAILED");
        values.put("failureCode", failureCode);
        var event =
                stepEvent(
                        context,
                        invocation,
                        ExecutionEventType.EXECUTOR_PLAN_STEP_FAILED,
                        ExecutionEventStatus.FAILED,
                        values,
                        at);
        return new ReportOutcome(successResult("FAILED", step.stepKey()), event);
    }

    private static ExecutorPlanStep requireStep(List<ExecutorPlanStep> steps, String stepKey) {
        return steps.stream()
                .filter(step -> step.stepKey().equals(stepKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("步骤不存在: " + stepKey));
    }

    private static ExecutionEvent stepEvent(
            InvocationContext context,
            ToolInvocation invocation,
            ExecutionEventType type,
            ExecutionEventStatus status,
            Map<String, Object> values,
            Instant at) {
        return new ExecutionEvent(
                new EventId("executor-plan-step-" + invocation.toolCallId()),
                context.tenantId(),
                context.conversationId(),
                context.sessionId(),
                context.taskId(),
                context.executionId(),
                context.runId(),
                context.parentExecutionId(),
                0,
                type,
                status,
                context.controlMode(),
                OwnerType.AGENT,
                context.assistantId(),
                null,
                context.userId(),
                context.correlationId(),
                context.causationId(),
                context.idempotencyKey(),
                new ExecutionEventPayload(values),
                at,
                context.nodeIdentity());
    }

    private static LinkedHashMap<String, Object> stepValues(
            String planId, String stepKey, String outcome) {
        var values = new LinkedHashMap<String, Object>();
        values.put("planId", planId);
        values.put("stepKey", stepKey);
        values.put("outcome", outcome);
        return values;
    }

    private static ToolInvocationResult successResult(String outcome, String stepKey) {
        var output = JsonUtils.toJsonString(Map.of("status", outcome, "stepKey", stepKey));
        return new ToolInvocationResult(output, Map.of("stepReported", true));
    }

    private static String requireArgument(Map<String, Object> arguments, String field) {
        var value = arguments.get(field);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("report_executor_step 缺少必填字段: " + field);
        }
        return value.toString();
    }

    private static String optionalArgument(Map<String, Object> arguments, String field) {
        var value = arguments.get(field);
        return value == null ? null : value.toString();
    }

    private record ReportOutcome(ToolInvocationResult result, ExecutionEvent event) {}
}
