package com.xuejiai.aaf.framework.intelligent.infrastructure.automation.spring;

import java.time.Clock;
import java.util.LinkedHashMap;

import com.xuejiai.aaf.framework.intelligent.assistant.application.AssistantCommand;
import com.xuejiai.aaf.framework.intelligent.assistant.application.TaskCommandService;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionContract;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskModelSelection;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskPlan;
import com.xuejiai.aaf.framework.intelligent.automation.model.AutomationRun;
import com.xuejiai.aaf.framework.intelligent.automation.port.AutomationPorts.DispatchPort;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.*;

/** 将确定性调度结果交给 P4 委托执行；Assistant 仅执行并解释结果。 */
public final class AutomationDelegatedDispatchAdapter implements DispatchPort {
    private final TaskCommandService coordinator;
    private final Clock clock;

    public AutomationDelegatedDispatchAdapter(TaskCommandService coordinator, Clock clock) {
        this.coordinator = coordinator;
        this.clock = clock;
    }

    @Override
    public void dispatch(AutomationRun run) {
        var now = clock.instant();
        var template = run.definitionSnapshot().template();
        var seed = run.runId();
        var contract = template.contract();
        var effectiveContract =
                new ExecutionContract(
                        contract.budget(),
                        now.plus(template.executionWindow()),
                        contract.maxModelCalls(),
                        contract.maxToolCalls(),
                        contract.allowedActions(),
                        contract.stopConditions(),
                        contract.retryPolicy(),
                        contract.notificationPolicy(),
                        contract.responsibleOwner(),
                        contract.takeoverPolicy());
        var taskId = run.taskId();
        var command =
                new AssistantCommand(
                        AssistantCommand.Operation.START,
                        run.tenantId(),
                        template.ownerId(),
                        template.memorySubject(),
                        template.assistantId(),
                        new ConversationId("automation:" + seed),
                        new SessionId("automation:" + seed),
                        taskId,
                        new ExecutionId("automation:" + seed),
                        new RunId(seed),
                        null,
                        new CorrelationId(stableId("correlation|" + seed)),
                        new CausationId(run.definitionSnapshot().sourceTaskId().value()),
                        new IdempotencyKey(
                                stableId(
                                        "idempotency|"
                                                + run.automationId()
                                                + "|"
                                                + run.triggerKey())),
                        ControlMode.DELEGATED,
                        effectiveContract,
                        null,
                        0,
                        renderGoal(template.goal(), run.parameters()),
                        template.completionCriteria(),
                        template.contextCandidates(),
                        TaskModelSelection.auto(),
                        com.xuejiai.aaf.framework.intelligent.assistant.application
                                .InvocationProfile.primary(
                                null,
                                com.xuejiai.aaf.framework.intelligent.assistant.application
                                        .AssistantInvocation.MemoryMode.DEFAULT,
                                java.util.List.of(),
                                com.xuejiai.aaf.framework.intelligent.assistant.model
                                        .ExecutionIntent.conversationalAuto(null)),
                        now);
        coordinator.submit(command, copyBoard(template.board(), taskId));
    }

    private static String renderGoal(String goal, java.util.Map<String, Object> parameters) {
        return parameters.isEmpty() ? goal : goal + "\n自动化参数: " + parameters;
    }

    private static TaskPlan copyBoard(TaskPlan source, TaskId taskId) {
        var tasks = new LinkedHashMap<String, TaskPlan.TaskNode>();
        source.nodes()
                .values()
                .forEach(
                        item ->
                                tasks.put(
                                        item.nodeId(),
                                        TaskPlan.TaskNode.pending(
                                                item.nodeId(),
                                                item.kind(),
                                                item.description(),
                                                item.dependsOn(),
                                                item.inputBindings(),
                                                item.roleKey(),
                                                item.skillKey(),
                                                item.modelSelection(),
                                                item.maxAttempts())));
        return new TaskPlan(taskId, source.goal(), source.maxParallelism(), tasks);
    }

    private static String stableId(String value) {
        return java.util
                .UUID
                .nameUUIDFromBytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString();
    }
}
