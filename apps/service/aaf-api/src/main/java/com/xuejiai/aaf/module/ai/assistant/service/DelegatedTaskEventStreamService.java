package com.xuejiai.aaf.module.ai.assistant.service;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventStorePort;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventStorePort.StoredExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.publication.AafAiTaskEvent;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.module.ai.event.HeadlessProjector;

import lombok.extern.slf4j.Slf4j;

/** 基于持久化 eventOffset 的委托任务 SSE；断线重连可能重复投递，消费者按 eventId/offset 去重。 */
@Service
@Slf4j
public class DelegatedTaskEventStreamService {

    private static final long SSE_TIMEOUT = 10 * 60 * 1000L;
    private static final long POLL_INTERVAL_MS = 250L;

    private final ExecutionEventStorePort events;
    private final HeadlessProjector projector;

    public DelegatedTaskEventStreamService(
            ExecutionEventStorePort events, HeadlessProjector projector) {
        this.events = events;
        this.projector = projector;
    }

    public SseEmitter subscribe(TenantId tenantId, TaskId taskId, long afterEventOffset) {
        if (afterEventOffset < 0) {
            throw new IllegalArgumentException("afterEventOffset 不能为负数");
        }
        var emitter = new SseEmitter(SSE_TIMEOUT);
        var closed = new AtomicBoolean();
        emitter.onCompletion(() -> closed.set(true));
        emitter.onTimeout(() -> closed.set(true));
        emitter.onError(ignored -> closed.set(true));
        Thread.startVirtualThread(
                () -> stream(tenantId, taskId, afterEventOffset, emitter, closed));
        return emitter;
    }

    private void stream(
            TenantId tenantId,
            TaskId taskId,
            long afterEventOffset,
            SseEmitter emitter,
            AtomicBoolean closed) {
        var cursor = new AtomicLong(afterEventOffset);
        var firstPoll = true;
        try {
            while (!closed.get()) {
                final List<StoredExecutionEvent> stored;
                if (firstPoll) {
                    var history =
                            events.readTask(tenantId, taskId, 0)
                                    .collectList()
                                    .blockOptional()
                                    .orElseGet(List::of);
                    stored =
                            history.stream()
                                    .filter(event -> event.eventOffset() > cursor.get())
                                    .toList();
                    if (stored.isEmpty()
                            && history.stream()
                                    .map(projector::project)
                                    .anyMatch(AafAiTaskEvent::terminal)) {
                        // 请求 cursor 已覆盖任务终态；严格 after-cursor，不重发带编号的旧事件。
                        closed.set(true);
                        emitter.complete();
                        return;
                    }
                    firstPoll = false;
                } else {
                    stored =
                            events.readTask(tenantId, taskId, cursor.get())
                                    .collectList()
                                    .blockOptional()
                                    .orElseGet(List::of);
                }
                for (var event : stored) {
                    var projected = projector.project(event);
                    emitter.send(
                            SseEmitter.event()
                                    .id(Long.toString(event.eventOffset()))
                                    .name(projected.type())
                                    .data(projected, MediaType.APPLICATION_JSON));
                    cursor.set(event.eventOffset());
                    if (projected.terminal()) {
                        closed.set(true);
                        emitter.complete();
                        return;
                    }
                }
                Thread.sleep(POLL_INTERVAL_MS);
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            closed.set(true);
            log.debug(
                    "[TaskEventStream] taskId={} cursor={} failureType={}",
                    taskId.value(),
                    cursor.get(),
                    failure.getClass().getName());
            emitter.complete();
        } catch (IOException failure) {
            closed.set(true);
            log.debug(
                    "[TaskEventStream] taskId={} cursor={} failureType={}",
                    taskId.value(),
                    cursor.get(),
                    failure.getClass().getName());
            emitter.complete();
        } catch (RuntimeException failure) {
            closed.set(true);
            log.debug(
                    "[TaskEventStream] taskId={} cursor={} failureType={}",
                    taskId.value(),
                    cursor.get(),
                    failure.getClass().getName());
            var terminal =
                    projector.terminalFailure(
                            taskId.value(), 1, "ASSISTANT_TASK_EVENT_STREAM_FAILED");
            try {
                emitter.send(
                        SseEmitter.event()
                                .name(terminal.type())
                                .data(terminal, MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (IOException ignored) {
                emitter.complete();
            }
        }
    }
}
