package com.xuejiai.aaf.framework.intelligent.infrastructure.governance;

import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;

import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskResumeSignalPort;

/** 将持久审批决定发布为外部可订阅的恢复信号，不轮询、不占执行线程。 */
public final class SpringTaskResumeSignalAdapter implements TaskResumeSignalPort {
    private final ApplicationEventPublisher publisher;

    public SpringTaskResumeSignalAdapter(ApplicationEventPublisher publisher) {
        this.publisher = Objects.requireNonNull(publisher, "publisher 不能为空");
    }

    @Override
    public void publish(ResumeSignal signal) {
        publisher.publishEvent(signal);
    }
}
