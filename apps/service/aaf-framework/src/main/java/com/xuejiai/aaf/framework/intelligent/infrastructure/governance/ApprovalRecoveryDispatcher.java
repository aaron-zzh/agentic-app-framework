package com.xuejiai.aaf.framework.intelligent.infrastructure.governance;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.context.event.EventListener;

import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantCommandPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskRecoveryDispatchPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskRecoveryPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskResumeSignalPort.ResumeSignal;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ExecutionEventStatus;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/** 无轮询 HITL 恢复消费方；恢复失败后作业回到 PENDING，可按 approvalId 重放。 */
public final class ApprovalRecoveryDispatcher implements TaskRecoveryDispatchPort {

    private final TaskRecoveryPort recoveries;
    private final AssistantCommandPort commands;

    public ApprovalRecoveryDispatcher(
            TaskRecoveryPort recoveries, AssistantCommandPort commands) {
        this.recoveries = Objects.requireNonNull(recoveries, "recoveries 不能为空");
        this.commands = Objects.requireNonNull(commands, "commands 不能为空");
    }

    @EventListener
    public void onResumeSignal(ResumeSignal signal) {
        if (signal.approved()) {
            recover(signal.tenantId(), signal.approvalId());
        }
    }

    @Override
    public boolean recover(TenantId tenantId, String approvalId) {
        var claimed = recoveries.claim(tenantId, approvalId, Instant.now());
        if (claimed.isEmpty()) {
            return false;
        }
        var job = claimed.get();
        var executionFailure = new AtomicReference<String>();
        try {
            commands.execute(job.command())
                    .doOnNext(event -> {
                        if (event.status() == ExecutionEventStatus.FAILED
                                || event.status() == ExecutionEventStatus.REJECTED) {
                            executionFailure.compareAndSet(
                                    null, "恢复执行返回终止状态: " + event.status());
                        }
                    })
                    .then()
                    .subscribe(
                            ignored -> {},
                            failure -> recoveries.release(
                                    tenantId,
                                    approvalId,
                                    failure.getMessage(),
                                    Instant.now()),
                            () -> {
                                var failure = executionFailure.get();
                                if (failure == null) {
                                    recoveries.complete(tenantId, approvalId, Instant.now());
                                } else {
                                    recoveries.release(
                                            tenantId, approvalId, failure, Instant.now());
                                }
                            });
            return true;
        } catch (RuntimeException failure) {
            recoveries.release(tenantId, approvalId, failure.getMessage(), Instant.now());
            throw failure;
        }
    }
}
