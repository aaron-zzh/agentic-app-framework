package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.spring;

import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;

import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskDispatchSignalPort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

public final class SpringTaskDispatchSignalAdapter implements TaskDispatchSignalPort {
    private final ApplicationEventPublisher publisher;

    public SpringTaskDispatchSignalAdapter(ApplicationEventPublisher publisher) {
        this.publisher = Objects.requireNonNull(publisher, "publisher 不能为空");
    }

    @Override
    public void signal(TenantId tenantId, TaskId taskId, String dispatchId) {
        if (dispatchId == null || dispatchId.isBlank()) {
            throw new IllegalArgumentException("dispatchId 不能为空白");
        }
        publisher.publishEvent(new DispatchSignal(tenantId, taskId, dispatchId));
    }

    public record DispatchSignal(TenantId tenantId, TaskId taskId, String dispatchId) {}
}
