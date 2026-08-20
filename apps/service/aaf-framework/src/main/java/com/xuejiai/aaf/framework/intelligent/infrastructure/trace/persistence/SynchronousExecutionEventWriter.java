package com.xuejiai.aaf.framework.intelligent.infrastructure.trace.persistence;

import java.util.Objects;

import org.springframework.dao.DataIntegrityViolationException;

import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventStorePort.StoredExecutionEvent;

/** 执行事件同步落库器；不发布 Spring 事件，由调用方选择直接发布或写入 outbox。 */
public final class SynchronousExecutionEventWriter {
    private final ExecutionEventRepository repository;

    public SynchronousExecutionEventWriter(ExecutionEventRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository 不能为空");
    }

    public StoredExecutionEvent requireStored(ExecutionEvent requested) {
        Objects.requireNonNull(requested, "requested 不能为空");
        var existing =
                repository
                        .findById(requested.eventId().value())
                        .map(ExecutionEventEntity::getEvent)
                        .orElseThrow(() -> new IllegalStateException("transition outbox 缺少对应任务事件"));
        return stored(requireSameEvent(existing, requested));
    }

    public WriteResult append(ExecutionEvent requested, long fencingToken) {
        Objects.requireNonNull(requested, "requested 不能为空");
        if (fencingToken < 0) {
            throw new IllegalArgumentException("fencingToken 不能为负数");
        }
        var existing = repository.findById(requested.eventId().value()).orElse(null);
        if (existing != null) {
            var event = requireSameEvent(existing.getEvent(), requested);
            return new WriteResult(stored(event), false);
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
        entity.setSequence(sequence);
        entity.setFencingToken(fencingToken);
        entity.setCreatedAt(event.createdAt());
        entity.setEvent(event);
        try {
            repository.saveAndFlush(entity);
            return new WriteResult(stored(event), true);
        } catch (DataIntegrityViolationException conflict) {
            var concurrent =
                    repository
                            .findById(event.eventId().value())
                            .map(ExecutionEventEntity::getEvent)
                            .orElseThrow(() -> conflict);
            return new WriteResult(stored(requireSameEvent(concurrent, requested)), false);
        }
    }

    private StoredExecutionEvent stored(ExecutionEvent event) {
        var eventOffset = repository.findEventOffsetByEventId(event.eventId().value());
        if (eventOffset == null) {
            throw new IllegalStateException("执行事件落库后缺少 eventOffset: " + event.eventId().value());
        }
        return new StoredExecutionEvent(eventOffset, event);
    }

    private static ExecutionEvent requireSameEvent(
            ExecutionEvent existing, ExecutionEvent requested) {
        if (!existing.equals(withSequence(requested, existing.sequence()))) {
            throw new IllegalStateException("eventId 已绑定不同事件内容: " + requested.eventId().value());
        }
        return existing;
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

    public record WriteResult(StoredExecutionEvent storedEvent, boolean created) {
        public WriteResult {
            Objects.requireNonNull(storedEvent, "storedEvent 不能为空");
        }
    }
}
