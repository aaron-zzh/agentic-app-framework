package com.xuejiai.aaf.module.ai.assistant.service;

import java.time.Duration;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.assistant.application.RunDeliveryTracker;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionLiveEventPort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.RunId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

/**
 * 为恢复后的任务打开实时事件订阅，不占用执行线程等待恢复。
 *
 * <p>订阅只依赖 {@code tenantId}/{@code taskId}，与具体是 {@code HumanApproval} 还是 {@code
 * ClarificationRequest} 恢复无关 （AAF-114 #11408 第二版）。
 *
 * <p>恢复流跟随<b>整个任务</b>而不是作出决定的那个执行：决定后当前节点和下游节点继续运行，直到交付节点完成、任一节点失败/取消/暂停， 或再次进入等待授权 / 澄清（此时流以新的
 * interrupt 结束，由用户逐个处理）。
 *
 * <p><b>必须先 open、后触发恢复</b>：实时订阅只能看到登记之后发布的事件，触发恢复后再 open 会漏掉最早的增量。
 */
@Service
@RequiredArgsConstructor
public class AssistantApprovalEventService {

    private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(5);

    private final ExecutionLiveEventPort liveEvents;

    /** 恢复点订阅：登记 {@code taskId} 的实时订阅，转发整个任务的事件，直到任务流收口或空闲超时。 审批与澄清恢复共用，调用方须在触发恢复之前调用。 */
    public RecoveryStream open(TenantId tenantId, TaskId taskId, RunId runId) {
        var source =
                liveEvents
                        .open(tenantId, taskId)
                        .takeUntil(new RunDeliveryTracker()::endsStream)
                        .timeout(STREAM_TIMEOUT);
        return new RecoveryStream(runId.value(), source);
    }

    public record RecoveryStream(String runId, Flux<ExecutionEvent> events) {
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
