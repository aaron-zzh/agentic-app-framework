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

    /** 会改变 Task 对用户可见状态的执行事件：开始、升级、角色切换、终态、暂停/恢复、等待授权/澄清/外部输入、控制权转移。 */
    private static final Set<ExecutionEventType> STATUS_EVENTS =
            Set.of(
                    ExecutionEventType.EXECUTION_STARTED,
                    ExecutionEventType.EXECUTION_PROMOTED,
                    ExecutionEventType.ROLE_SWITCHED,
                    ExecutionEventType.EXECUTION_COMPLETED,
                    ExecutionEventType.EXECUTION_FAILED,
                    ExecutionEventType.EXECUTION_CANCELED,
                    ExecutionEventType.EXECUTION_PAUSED,
                    ExecutionEventType.EXECUTION_RESUMED,
                    ExecutionEventType.TASK_STATUS_CHANGED,
                    ExecutionEventType.APPROVAL_REQUESTED,
                    ExecutionEventType.APPROVAL_RESOLVED,
                    // 生产授权链路由 DefaultToolGateway 提交 AUTHORIZATION_*，缺失时看板只能等兜底轮询才弹审批卡
                    ExecutionEventType.AUTHORIZATION_REQUESTED,
                    ExecutionEventType.AUTHORIZATION_GRANTED,
                    ExecutionEventType.AUTHORIZATION_DENIED,
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

    /** 新落库的执行事件回调；与事件同事务发布，回滚时不会送达。无法确定接收用户或任务身份时跳过。 */
    public void onEventCreated(ExecutionEvent event) {
        if (event.userId() == null || !STATUS_EVENTS.contains(event.type())) {
            return;
        }
        var taskId = taskKey(event);
        if (taskId == null) {
            return;
        }
        Long ownerId;
        try {
            ownerId = Long.valueOf(event.userId().value());
        } catch (NumberFormatException notNumeric) {
            log.debug("Task 状态事件的 userId 不是数字，已跳过: eventId={}", event.eventId().value());
            return;
        }
        var values = event.payload().values();
        var payload = new LinkedHashMap<String, Object>();
        payload.put("taskId", taskId);
        payload.put("executionId", event.executionId().value());
        payload.put("conversationId", event.conversationId().value());
        payload.put("eventType", AafAiTaskEventRegistry.descriptor(event.type()).type());
        payload.put("status", String.valueOf(event.status()));
        var node = event.nodeIdentity();
        if (node != null) {
            payload.put("nodeId", node.nodeId());
            payload.put("nodeKind", node.kind().name());
            if (node.roleKey() != null) {
                payload.put("roleKey", node.roleKey());
            }
        }
        if (event.type() == ExecutionEventType.ROLE_SWITCHED) {
            if (event.taskId() == null) {
                payload.put("scope", "DIRECT");
            }
            if (values.get("toRoleKey") instanceof String toRoleKey) {
                payload.put("toRoleKey", toRoleKey);
            }
        }
        if (event.type() == ExecutionEventType.EXECUTION_PROMOTED
                && values.get("roleKey") instanceof String promotedRoleKey) {
            payload.putIfAbsent("roleKey", promotedRoleKey);
        }
        try {
            publisher.publish(ownerId, EVENT_TYPE, Map.of(), payload);
            log.debug(
                    "[看板事件] 已发布：type={}，taskId={}，eventType={}，nodeKind={}",
                    EVENT_TYPE,
                    taskId,
                    event.type(),
                    node == null ? null : node.kind());
        } catch (RuntimeException failure) {
            // 通知失败不得影响 Task 状态事实的落库；前端轮询兜底
            log.warn("Task 状态事件发布失败: eventId={}", event.eventId().value(), failure);
        }
    }

    /**
     * 事件对应的看板任务键：升级事件落在 DIRECT 执行上、taskId 为空，取 payload 的 {@code promotedTaskId}； 轻量态角色切换没有 Task，用
     * executionId。其余事件没有 taskId 时跳过。
     */
    private static String taskKey(ExecutionEvent event) {
        if (event.taskId() != null) {
            return event.taskId().value();
        }
        return switch (event.type()) {
            case EXECUTION_PROMOTED ->
                    event.payload().values().get("promotedTaskId") instanceof String promoted
                                    && !promoted.isBlank()
                            ? promoted
                            : null;
            case ROLE_SWITCHED -> event.executionId().value();
            default -> null;
        };
    }
}
