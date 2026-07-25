package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.spring;

import java.util.Objects;

import com.xuejiai.aaf.framework.engine.task.agent.AgentTask;
import com.xuejiai.aaf.framework.engine.task.agent.AgentTaskContext;
import com.xuejiai.aaf.framework.engine.task.agent.AgentTaskOutcome;
import com.xuejiai.aaf.framework.intelligent.assistant.application.DelegatedTaskCoordinator;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask.Status;
import com.xuejiai.aaf.framework.intelligent.assistant.port.DelegatedTaskPort;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ExecutionEventStatus;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/** 将持久委托任务接入统一 AgentTaskRuntime。 */
public final class DelegatedTaskAgentTaskAdapter implements AgentTask {

    public static final String TASK_TYPE = "delegated-task";

    private final DelegatedTaskCoordinator coordinator;
    private final DelegatedTaskPort tasks;

    public DelegatedTaskAgentTaskAdapter(
            DelegatedTaskCoordinator coordinator, DelegatedTaskPort tasks) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator 不能为空");
        this.tasks = Objects.requireNonNull(tasks, "tasks 不能为空");
    }

    @Override
    public String taskType() {
        return TASK_TYPE;
    }

    @Override
    public AgentTaskOutcome execute(AgentTaskContext context) {
        var tenantId = new TenantId(context.tenantId());
        var taskId = new TaskId(context.taskId());
        ExecutionEvent lastEvent = null;
        RuntimeException dispatchFailure = null;
        try {
            lastEvent = coordinator
                    .dispatch(tenantId, taskId, context.lease().ownerId())
                    .blockLast();
        } catch (RuntimeException failure) {
            dispatchFailure = failure;
        }

        var task = tasks.find(tenantId, taskId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "委托任务不存在: " + taskId.value()))
                .task();
        return switch (task.status()) {
            case COMPLETED -> AgentTaskOutcome.completed();
            case FAILED, CANCELED -> AgentTaskOutcome.terminal(detail(task.status(), dispatchFailure));
            case PAUSED, AWAITING_INPUT, AWAITING_AUTHORIZATION ->
                    AgentTaskOutcome.pending(task.status().name());
            case PENDING -> isRetryable(task.consecutiveFailures(), lastEvent, dispatchFailure)
                    ? AgentTaskOutcome.retryable(detail(task.status(), dispatchFailure))
                    : AgentTaskOutcome.pending(task.status().name());
            case RUNNING -> AgentTaskOutcome.pending(task.status().name());
        };
    }

    private static boolean isRetryable(
            int consecutiveFailures, ExecutionEvent lastEvent, RuntimeException dispatchFailure) {
        if (consecutiveFailures > 0 || dispatchFailure != null) {
            return true;
        }
        return lastEvent != null
                && (lastEvent.status() == ExecutionEventStatus.FAILED
                        || lastEvent.status() == ExecutionEventStatus.REJECTED);
    }

    private static String detail(Status status, RuntimeException failure) {
        if (failure == null || failure.getMessage() == null || failure.getMessage().isBlank()) {
            return status.name();
        }
        return failure.getMessage();
    }
}
