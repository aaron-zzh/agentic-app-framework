package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.spring;

import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;

import com.xuejiai.aaf.framework.intelligent.assistant.port.DelegatedTaskDispatchPort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

public final class SpringDelegatedTaskDispatchAdapter implements DelegatedTaskDispatchPort {
    private final ApplicationEventPublisher publisher;

    public SpringDelegatedTaskDispatchAdapter(ApplicationEventPublisher publisher) {
        this.publisher = Objects.requireNonNull(publisher, "publisher 不能为空");
    }

    @Override
    public void signal(TenantId tenantId, TaskId taskId) {
        publisher.publishEvent(new DispatchSignal(tenantId, taskId));
    }

    public record DispatchSignal(TenantId tenantId, TaskId taskId) {}
}
