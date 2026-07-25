package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.spring;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

import com.xuejiai.aaf.framework.intelligent.assistant.application.DelegatedTaskCoordinator;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.spring.SpringDelegatedTaskDispatchAdapter.DispatchSignal;

/** 事件即时派发 + 定时恢复；执行流为 Reactor 异步订阅，不轮询占线程等待。 */
public final class DelegatedTaskScheduler {
    private static final Logger LOG =
            LoggerFactory.getLogger(DelegatedTaskScheduler.class);
    private final DelegatedTaskCoordinator coordinator;
    private final String workerId;

    public DelegatedTaskScheduler(DelegatedTaskCoordinator coordinator, String workerId) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator 不能为空");
        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("workerId 不能为空白");
        }
        this.workerId = workerId;
    }

    @EventListener
    public void onDispatch(DispatchSignal signal) {
        coordinator.dispatch(signal.tenantId(), signal.taskId(), workerId)
                .subscribe(
                        ignored -> {},
                        failure -> LOG.error(
                                "委托任务派发失败: taskId={}", signal.taskId().value(), failure));
    }

    @Scheduled(fixedDelayString = "${aaf.assistant.delegated.recovery-delay-ms:30000}")
    public void recover() {
        coordinator.recoverAndDispatch(workerId, 32);
    }
}
