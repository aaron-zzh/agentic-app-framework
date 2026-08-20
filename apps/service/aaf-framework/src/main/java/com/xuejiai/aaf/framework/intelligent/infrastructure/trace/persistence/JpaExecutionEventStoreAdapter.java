package com.xuejiai.aaf.framework.intelligent.infrastructure.trace.persistence;

import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;

import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort.Lease;
import com.xuejiai.aaf.framework.intelligent.assistant.port.DelegatedTaskPort;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventStorePort;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventStorePort.StoredExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** ai_task_event 唯一 append-only 写入与续读实现。 */
public final class JpaExecutionEventStoreAdapter
        implements ExecutionEventStorePort, ApplicationEventPublisherAware {
    private final ExecutionEventRepository repository;
    private final SynchronousExecutionEventWriter writer;
    private final ConversationLeasePort leases;
    private final DelegatedTaskPort tasks;
    private ApplicationEventPublisher applicationEventPublisher;

    public JpaExecutionEventStoreAdapter(
            ExecutionEventRepository repository,
            SynchronousExecutionEventWriter writer,
            ConversationLeasePort leases,
            DelegatedTaskPort tasks) {
        this.repository = Objects.requireNonNull(repository, "repository 不能为空");
        this.writer = Objects.requireNonNull(writer, "writer 不能为空");
        this.leases = Objects.requireNonNull(leases, "leases 不能为空");
        this.tasks = Objects.requireNonNull(tasks, "tasks 不能为空");
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher =
                Objects.requireNonNull(applicationEventPublisher, "applicationEventPublisher 不能为空");
    }

    @Override
    public Mono<ExecutionEvent> append(ExecutionEvent event, Lease lease) {
        requireLease(event, lease);
        return Mono.fromCallable(() -> appendBlocking(event, lease))
                .subscribeOn(Schedulers.boundedElastic());
    }

    ExecutionEvent appendBlocking(ExecutionEvent requested, Lease lease) {
        requireLease(requested, lease);
        var result = writer.append(requested, lease == null ? 0L : lease.fencingToken());
        if (result.created()) {
            publishStoredEvent(result.storedEvent());
        }
        return result.storedEvent().event();
    }

    @Override
    public Flux<StoredExecutionEvent> readTask(
            TenantId tenantId, TaskId taskId, long afterEventOffset) {
        return Mono.fromCallable(
                        () ->
                                repository
                                        .findByTenantIdAndTaskIdAndEventOffsetGreaterThanOrderByEventOffsetAsc(
                                                tenantId.value(), taskId.value(), afterEventOffset))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable)
                .map(
                        entity ->
                                new StoredExecutionEvent(
                                        entity.getEventOffset(), entity.getEvent()));
    }

    @Override
    public Flux<ExecutionEvent> readExecution(
            TenantId tenantId, ExecutionId executionId, long afterSequence) {
        return Mono.fromCallable(
                        () ->
                                repository
                                        .findByTenantIdAndExecutionIdAndSequenceGreaterThanOrderBySequenceAsc(
                                                tenantId.value(),
                                                executionId.value(),
                                                afterSequence))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable)
                .map(ExecutionEventEntity::getEvent);
    }

    @Override
    public Mono<Long> nextSequence(TenantId tenantId, ExecutionId executionId, Lease lease) {
        if (lease != null) {
            leases.requireCurrent(lease);
        }
        return Mono.fromCallable(
                        () -> repository.allocateSequence(tenantId.value(), executionId.value()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private void publishStoredEvent(StoredExecutionEvent event) {
        Objects.requireNonNull(applicationEventPublisher, "ApplicationEventPublisher 尚未注入")
                .publishEvent(event);
    }

    private void requireLease(ExecutionEvent event, Lease lease) {
        if (event.controlMode()
                != com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode
                        .DELEGATED) {
            return;
        }
        if (lease == null
                || !event.tenantId().equals(lease.tenantId())
                || !event.conversationId().equals(lease.conversationId())) {
            throw new IllegalStateException("DELEGATED 事件写入缺少匹配 conversation lease");
        }
        leases.requireCurrent(lease);
        if (event.ownerType()
                != com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.OwnerType
                        .HUMAN) {
            tasks.requireExecution(event.tenantId(), event.taskId(), event.executionId(), lease);
        }
    }
}
