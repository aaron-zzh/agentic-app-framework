package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.spring;

import java.util.Objects;

import com.xuejiai.aaf.framework.engine.task.agent.AgentTask;
import com.xuejiai.aaf.framework.engine.task.agent.AgentTaskContext;
import com.xuejiai.aaf.framework.engine.task.agent.AgentTaskOutcome;
import com.xuejiai.aaf.framework.intelligent.assistant.application.TaskCommandService;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/** 将持久委托任务接入统一 AgentTaskRuntime。 */
public final class TaskDispatchAgentTaskAdapter implements AgentTask {

    public static final String TASK_TYPE = TaskCommandService.TASK_NODE_DISPATCH_TYPE;

    private final TaskCommandService coordinator;

    public TaskDispatchAgentTaskAdapter(TaskCommandService coordinator) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator 不能为空");
    }

    @Override
    public String taskType() {
        return TASK_TYPE;
    }

    @Override
    public AgentTaskOutcome execute(AgentTaskContext context) {
        var tenantId = new TenantId(context.tenantId());
        try {
            coordinator.dispatch(tenantId, context.taskId(), context.lease().ownerId()).blockLast();
            return AgentTaskOutcome.completed();
        } catch (RuntimeException failure) {
            return AgentTaskOutcome.retryable(
                    Objects.requireNonNullElse(failure.getMessage(), "task dispatch failed"));
        }
    }
}
