package com.xuejiai.aaf.module.ai.assistant.service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.assistant.model.HumanApproval;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventStorePort;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventStorePort.StoredExecutionEvent;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

/** 按审批恢复点续读持久化 Assistant 事件，不占用执行线程等待恢复。 */
@Service
@RequiredArgsConstructor
public class AssistantApprovalEventService {

    private static final Duration POLL_INTERVAL = Duration.ofMillis(250);
    private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(5);

    private final ExecutionEventStorePort events;

    public RecoveryStream stream(HumanApproval approval) {
        if (approval.status() != HumanApproval.Status.APPROVED || approval.decidedAt() == null) {
            throw new IllegalArgumentException("仅已批准审批可续读恢复事件");
        }
        var context = approval.invocationContext();
        var targetExecutionId =
                context.parentExecutionId() == null
                        ? context.executionId()
                        : context.parentExecutionId();
        var cursor = new AtomicLong();
        var source =
                Flux.interval(Duration.ZERO, POLL_INTERVAL)
                        .concatMap(
                                ignored ->
                                        events.readTask(
                                                        context.tenantId(),
                                                        context.taskId(),
                                                        cursor.get())
                                                .doOnNext(
                                                        stored ->
                                                                cursor.accumulateAndGet(
                                                                        stored.eventOffset(),
                                                                        Math::max))
                                                .filter(
                                                        stored ->
                                                                !stored.event()
                                                                        .createdAt()
                                                                        .isBefore(
                                                                                approval
                                                                                        .decidedAt()))
                                                .filter(
                                                        stored ->
                                                                stored.event()
                                                                        .executionId()
                                                                        .equals(targetExecutionId)))
                        .takeUntil(stored -> stored.event().isTerminal())
                        .timeout(STREAM_TIMEOUT);
        return new RecoveryStream(context.runId().value(), source);
    }

    public record RecoveryStream(String runId, Flux<StoredExecutionEvent> events) {
        public RecoveryStream {
            if (runId == null || runId.isBlank()) {
                throw new IllegalArgumentException("runId 不能为空白");
            }
            if (events == null) {
                throw new IllegalArgumentException("events 不能为空");
            }
        }
    }
}
