package com.xuejiai.aaf.framework.intelligent.infrastructure.automation.spring;

import java.time.Clock;
import java.util.LinkedHashMap;

import com.xuejiai.aaf.framework.intelligent.assistant.application.AssistantCommand;
import com.xuejiai.aaf.framework.intelligent.assistant.application.DelegatedTaskCoordinator;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionContract;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskBoard;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskModelSelection;
import com.xuejiai.aaf.framework.intelligent.automation.model.AutomationRun;
import com.xuejiai.aaf.framework.intelligent.automation.port.AutomationPorts.DispatchPort;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.*;

/** 将确定性调度结果交给 P4 委托执行；Assistant 仅执行并解释结果。 */
public final class AutomationDelegatedDispatchAdapter implements DispatchPort {
    private final DelegatedTaskCoordinator coordinator;
    private final Clock clock;

    public AutomationDelegatedDispatchAdapter(DelegatedTaskCoordinator coordinator, Clock clock) {
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
        var taskId = run.delegatedTaskId();
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
                        now);
        coordinator.submit(command, copyBoard(template.board(), taskId));
    }

    private static String renderGoal(String goal, java.util.Map<String, Object> parameters) {
        return parameters.isEmpty() ? goal : goal + "\n自动化参数: " + parameters;
    }

    private static TaskBoard copyBoard(TaskBoard source, TaskId taskId) {
        var tasks = new LinkedHashMap<String, TaskBoard.SubTask>();
        source.subTasks()
                .values()
                .forEach(
                        item ->
                                tasks.put(
                                        item.subTaskId(),
                                        TaskBoard.SubTask.pending(
                                                item.subTaskId(),
                                                item.description(),
                                                item.dependsOn(),
                                                item.maxAttempts())));
        return new TaskBoard(taskId, source.goal(), source.maxParallelism(), tasks);
    }

    private static String stableId(String value) {
        return java.util
                .UUID
                .nameUUIDFromBytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString();
    }
}
