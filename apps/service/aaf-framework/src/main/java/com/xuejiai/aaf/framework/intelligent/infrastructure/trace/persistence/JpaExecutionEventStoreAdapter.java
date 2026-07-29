package com.xuejiai.aaf.framework.intelligent.infrastructure.trace.persistence;

import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.dao.DataIntegrityViolationException;

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
    private final ConversationLeasePort leases;
    private final DelegatedTaskPort tasks;
    private ApplicationEventPublisher applicationEventPublisher;

    public JpaExecutionEventStoreAdapter(
            ExecutionEventRepository repository,
            ConversationLeasePort leases,
            DelegatedTaskPort tasks) {
        this.repository = Objects.requireNonNull(repository, "repository 不能为空");
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
        var existing = repository.findById(requested.eventId().value());
        if (existing.isPresent()) {
            return requireSameEvent(existing.get().getEvent(), requested);
        }
        var sequence =
                repository.allocateSequence(
                        requested.tenantId().value(), requested.executionId().value());
        var event = withSequence(requested, sequence);
        var entity = new ExecutionEventEntity();
        entity.setEventId(event.eventId().value());
        entity.setTenantId(event.tenantId().value());
        entity.setTaskId(event.taskId().value());
        entity.setExecutionId(event.executionId().value());
        entity.setSequence(event.sequence());
        entity.setFencingToken(lease == null ? 0L : lease.fencingToken());
        entity.setCreatedAt(event.createdAt());
        entity.setEvent(event);
        try {
            var storedEvent = repository.saveAndFlush(entity).getEvent();
            publishStoredEvent(storedEvent);
            return storedEvent;
        } catch (DataIntegrityViolationException conflict) {
            var concurrent =
                    repository
                            .findById(event.eventId().value())
                            .map(ExecutionEventEntity::getEvent)
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "execution sequence 分配后写入失败: "
                                                            + event.executionId().value()
                                                            + "#"
                                                            + event.sequence(),
                                                    conflict));
            return requireSameEvent(concurrent, requested);
        }
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

    private void publishStoredEvent(ExecutionEvent event) {
        var eventOffset = repository.findEventOffsetByEventId(event.eventId().value());
        if (eventOffset == null) {
            throw new IllegalStateException("执行事件落库后缺少 eventOffset: " + event.eventId().value());
        }
        Objects.requireNonNull(applicationEventPublisher, "ApplicationEventPublisher 尚未注入")
                .publishEvent(new StoredExecutionEvent(eventOffset, event));
    }

    private static ExecutionEvent requireSameEvent(
            ExecutionEvent existing, ExecutionEvent requested) {
        if (!existing.equals(withSequence(requested, existing.sequence()))) {
            throw new IllegalStateException("eventId 已绑定不同事件内容: " + requested.eventId().value());
        }
        return existing;
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

    private static ExecutionEvent withSequence(ExecutionEvent event, long sequence) {
        return new ExecutionEvent(
                event.eventId(),
                event.tenantId(),
                event.conversationId(),
                event.sessionId(),
                event.taskId(),
                event.executionId(),
                event.runId(),
                event.parentExecutionId(),
                sequence,
                event.type(),
                event.status(),
                event.controlMode(),
                event.ownerType(),
                event.assistantId(),
                event.agentId(),
                event.userId(),
                event.correlationId(),
                event.causationId(),
                event.idempotencyKey(),
                event.payload(),
                event.createdAt());
    }
}
