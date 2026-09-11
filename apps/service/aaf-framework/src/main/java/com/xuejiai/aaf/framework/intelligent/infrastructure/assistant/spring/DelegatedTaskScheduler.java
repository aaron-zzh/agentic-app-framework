package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.spring;

import java.util.Objects;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

import com.xuejiai.aaf.framework.engine.task.agent.AgentTaskRuntime;
import com.xuejiai.aaf.framework.intelligent.assistant.application.DelegatedTaskCoordinator;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.spring.SpringDelegatedTaskDispatchAdapter.DispatchSignal;

/** 事件即时派发经虚拟线程交给统一 AgentTaskRuntime 执行；定时恢复保留批量扫描语义。 */
public final class DelegatedTaskScheduler {
    private final DelegatedTaskCoordinator coordinator;
    private final AgentTaskRuntime agentTaskRuntime;
    private final String workerId;

    public DelegatedTaskScheduler(
            DelegatedTaskCoordinator coordinator,
            AgentTaskRuntime agentTaskRuntime,
            String workerId) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator 不能为空");
        this.agentTaskRuntime = Objects.requireNonNull(agentTaskRuntime, "agentTaskRuntime 不能为空");
        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("workerId 不能为空白");
        }
        this.workerId = workerId;
    }

    @EventListener
    public void onDispatch(DispatchSignal signal) {
        Thread.ofVirtual()
                .name("delegated-task-dispatch-" + signal.taskId().value())
                .start(
                        () ->
                                agentTaskRuntime.dispatch(
                                        DelegatedTaskAgentTaskAdapter.TASK_TYPE,
                                        signal.taskId().value(),
                                        signal.tenantId().value(),
                                        "EVENT"));
    }

    @Scheduled(fixedDelayString = "${aaf.assistant.delegated.recovery-delay-ms:30000}")
    public void recover() {
        coordinator.recoverAndDispatch(workerId, 32);
    }
}
