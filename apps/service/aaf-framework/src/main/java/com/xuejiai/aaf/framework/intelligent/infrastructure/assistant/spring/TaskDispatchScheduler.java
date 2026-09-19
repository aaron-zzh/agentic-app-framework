package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.spring;

import java.util.Objects;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.xuejiai.aaf.framework.engine.task.agent.AgentTaskRuntime;
import com.xuejiai.aaf.framework.intelligent.assistant.application.TaskCommandService;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ArtifactCompletionContract.CompletionSignal;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.spring.SpringTaskDispatchSignalAdapter.DispatchSignal;

import lombok.extern.slf4j.Slf4j;

/** 事件即时派发经虚拟线程交给统一 AgentTaskRuntime 执行；定时恢复保留批量扫描语义。 */
@Slf4j
public final class TaskDispatchScheduler {
    private final TaskCommandService taskCommands;
    private final AgentTaskRuntime agentTaskRuntime;
    private final String workerId;

    public TaskDispatchScheduler(
            TaskCommandService taskCommands, AgentTaskRuntime agentTaskRuntime, String workerId) {
        this.taskCommands = Objects.requireNonNull(taskCommands, "taskCommands 不能为空");
        this.agentTaskRuntime = Objects.requireNonNull(agentTaskRuntime, "agentTaskRuntime 不能为空");
        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("workerId 不能为空白");
        }
        this.workerId = workerId;
    }

    @EventListener
    public void onDispatch(DispatchSignal signal) {
        Thread.ofVirtual()
                .name("task-dispatch-" + signal.taskId().value())
                .start(
                        () ->
                                agentTaskRuntime.dispatch(
                                        TaskDispatchAgentTaskAdapter.TASK_TYPE,
                                        signal.dispatchId(),
                                        signal.tenantId().value(),
                                        "EVENT"));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onExternalArtifact(CompletionSignal signal) {
        try {
            taskCommands.recoverExternal(signal.executionRunId());
        } catch (RuntimeException failure) {
            log.warn(
                    "外部制品即时恢复失败，等待 scheduler 补偿：executionRunId={}，错误={}",
                    signal.executionRunId(),
                    failure.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${aaf.assistant.task-dispatch.recovery-delay-ms:30000}")
    public void recover() {
        taskCommands.recoverAndDispatch(workerId, 32);
    }
}
