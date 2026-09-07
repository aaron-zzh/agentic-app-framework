package com.xuejiai.aaf.module.ai.assistant.service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.assistant.model.HumanApproval;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventStorePort;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventStorePort.StoredExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.RunId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

/**
 * 按恢复点续读持久化 Assistant 事件，不占用执行线程等待恢复。
 *
 * <p>底层轮询实现只依赖 {@code tenantId}/{@code taskId}/{@code executionId}/恢复时刻，与具体是
 * {@code HumanApproval} 还是 {@code ClarificationRequest} 恢复无关（AAF-114 #11408 第二版，已核实）；
 * {@link #streamByExecutionId} 是通用入口，{@link #stream(HumanApproval)} 收窄为薄封装保持既有行为不变。
 */
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
        return streamByExecutionId(
                context.tenantId(),
                context.taskId(),
                targetExecutionId,
                context.runId(),
                approval.decidedAt());
    }

    /**
     * 通用恢复点续读：按 {@code taskId} 轮询持久化事件，过滤出 {@code resumedAt} 之后、属于
     * {@code targetExecutionId} 的事件，直到遇到终态事件或超时。
     *
     * @param resumedAt 恢复动作发生的时刻（approval 的 {@code decidedAt}，或 clarification 提交的
     *     {@code ExecutionInput.receivedAt}）；只返回该时刻之后产生的事件，避免把恢复前的旧事件重放一次。
     */
    public RecoveryStream streamByExecutionId(
            TenantId tenantId,
            TaskId taskId,
            ExecutionId targetExecutionId,
            RunId runId,
            Instant resumedAt) {
        var cursor = new AtomicLong();
        var source =
                Flux.interval(Duration.ZERO, POLL_INTERVAL)
                        .concatMap(
                                ignored ->
                                        events.readTask(tenantId, taskId, cursor.get())
                                                .doOnNext(
                                                        stored ->
                                                                cursor.accumulateAndGet(
                                                                        stored.eventOffset(),
                                                                        Math::max))
                                                .filter(
                                                        stored ->
                                                                !stored.event()
                                                                        .createdAt()
                                                                        .isBefore(resumedAt))
                                                .filter(
                                                        stored ->
                                                                stored.event()
                                                                        .executionId()
                                                                        .equals(
                                                                                targetExecutionId)))
                        .takeUntil(stored -> stored.event().isTerminal())
                        .timeout(STREAM_TIMEOUT);
        return new RecoveryStream(runId.value(), source);
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
