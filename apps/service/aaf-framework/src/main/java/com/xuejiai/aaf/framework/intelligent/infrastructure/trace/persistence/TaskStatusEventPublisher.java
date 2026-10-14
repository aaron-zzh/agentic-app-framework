package com.xuejiai.aaf.framework.intelligent.infrastructure.trace.persistence;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.framework.intelligent.shared.event.publication.AafAiTaskEventRegistry;
import com.xuejiai.aaf.framework.messaging.event.SysEventPublisher;

import lombok.extern.slf4j.Slf4j;

/**
 * 把 canonical Task 的状态类执行事件投影为全局事件 {@code task.status.changed}。
 *
 * <p>只做"变化通知"：前端据此失效 Task 查询并重新拉取权威状态，载荷不含任务结果。Task 与 Execution 的 ID 是字符串，不满足事件 {@code refs}
 * 的正整数约束， 因此放进 {@code payload}，由前端按 {@code conversationId} / {@code taskId} 匹配。
 */
@Slf4j
public final class TaskStatusEventPublisher {

    /** 事件类型，属智能层 Task 域，不带 {@code aigc.} 前缀。 */
    public static final String EVENT_TYPE = "task.status.changed";

    /** 会改变 Task 对用户可见状态的执行事件：终态、暂停/恢复、等待授权/澄清/外部输入、控制权转移。 */
    private static final Set<ExecutionEventType> STATUS_EVENTS =
            Set.of(
                    ExecutionEventType.EXECUTION_COMPLETED,
                    ExecutionEventType.EXECUTION_FAILED,
                    ExecutionEventType.EXECUTION_CANCELED,
                    ExecutionEventType.EXECUTION_PAUSED,
                    ExecutionEventType.EXECUTION_RESUMED,
                    ExecutionEventType.TASK_STATUS_CHANGED,
                    ExecutionEventType.APPROVAL_REQUESTED,
                    ExecutionEventType.APPROVAL_RESOLVED,
                    ExecutionEventType.CLARIFICATION_REQUESTED,
                    ExecutionEventType.CLARIFICATION_RESOLVED,
                    ExecutionEventType.CLARIFICATION_CANCELED,
                    ExecutionEventType.CLARIFICATION_EXPIRED,
                    ExecutionEventType.EXTERNAL_EXECUTION_REQUESTED,
                    ExecutionEventType.EXTERNAL_EXECUTION_SUPPLIED,
                    ExecutionEventType.OWNERSHIP_TRANSFERRED);

    private final SysEventPublisher publisher;

    public TaskStatusEventPublisher(SysEventPublisher publisher) {
        this.publisher = Objects.requireNonNull(publisher, "publisher 不能为空");
    }

    /** 新落库的执行事件回调；与事件同事务发布，回滚时不会送达。无法确定接收用户时跳过。 */
    public void onEventCreated(ExecutionEvent event) {
        if (event.taskId() == null
                || event.userId() == null
                || !STATUS_EVENTS.contains(event.type())) {
            return;
        }
        Long ownerId;
        try {
            ownerId = Long.valueOf(event.userId().value());
        } catch (NumberFormatException notNumeric) {
            log.debug("Task 状态事件的 userId 不是数字，已跳过: eventId={}", event.eventId().value());
            return;
        }
        var payload = new LinkedHashMap<String, Object>();
        payload.put("taskId", event.taskId().value());
        payload.put("executionId", event.executionId().value());
        payload.put("conversationId", event.conversationId().value());
        payload.put("eventType", AafAiTaskEventRegistry.descriptor(event.type()).type());
        payload.put("status", String.valueOf(event.status()));
        try {
            publisher.publish(ownerId, EVENT_TYPE, Map.of(), payload);
        } catch (RuntimeException failure) {
            // 通知失败不得影响 Task 状态事实的落库；前端轮询兜底
            log.warn("Task 状态事件发布失败: eventId={}", event.eventId().value(), failure);
        }
    }
}
