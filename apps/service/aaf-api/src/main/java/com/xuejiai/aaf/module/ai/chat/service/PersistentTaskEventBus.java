package com.xuejiai.aaf.module.ai.chat.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.engine.task.TaskEventBus;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ExecutionEventStatus;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.OwnerType;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventPayload;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventStorePort;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.CorrelationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.EventId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.RunId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;
import com.xuejiai.aaf.module.ai.chat.domain.TaskEvent;
import com.xuejiai.aaf.module.ai.chat.repository.ChatTaskRepository;

import lombok.RequiredArgsConstructor;

/** 旧聊天任务事件桥接器：只向 ExecutionEventStorePort 追加，再做 SSE 广播。 */
@Component
@RequiredArgsConstructor
public class PersistentTaskEventBus implements TaskEventBus {

    private final ChatTaskRepository taskRepository;
    private final ExecutionEventStorePort eventStore;
    private final TaskEventStreamService eventStreamService;

    @Override
    @Transactional(readOnly = true)
    public void publish(
            Long taskId, Long executionId, String subtaskKey, String type, String payloadJson) {
        var task = taskRepository.findById(taskId).orElseThrow();
        if (task.getOrgId() == null) {
            throw new IllegalStateException("聊天任务缺少 tenant/org 归属: " + taskId);
        }
        if (executionId == null) {
            throw new IllegalArgumentException("任务事件必须携带 executionId");
        }

        var payload = new LinkedHashMap<String, Object>();
        payload.put("legacyType", type);
        if (subtaskKey != null) {
            payload.put("subtaskKey", subtaskKey);
        }
        if (payloadJson != null) {
            payload.put("legacyPayload", payloadJson);
        }

        var event = new ExecutionEvent(
                new EventId(UUID.randomUUID().toString()),
                new TenantId(task.getOrgId().toString()),
                new ConversationId(task.getConversationId().toString()),
                new SessionId(task.getConversationId().toString()),
                new TaskId(taskId.toString()),
                new ExecutionId(executionId.toString()),
                new RunId(executionId.toString()),
                null,
                1,
                eventType(type, subtaskKey),
                eventStatus(type, subtaskKey),
                ControlMode.COLLABORATIVE,
                OwnerType.HUMAN,
                null,
                null,
                new UserId(task.getCreatorId().toString()),
                new CorrelationId(taskId.toString()),
                null,
                null,
                new ExecutionEventPayload(payload),
                Instant.now());
        var stored = eventStore.append(event).block();
        if (stored == null) {
            throw new IllegalStateException("任务事件追加未返回结果: " + taskId);
        }
        var offset = eventStore.readTask(stored.tenantId(), stored.taskId(), 0)
                .filter(candidate -> candidate.event().eventId().equals(stored.eventId()))
                .map(ExecutionEventStorePort.StoredExecutionEvent::eventOffset)
                .blockFirst();
        if (offset == null) {
            throw new IllegalStateException("任务事件追加后缺少 eventOffset: " + stored.eventId().value());
        }
        eventStreamService.broadcast(TaskEvent.from(offset, stored));
    }

    private static ExecutionEventType eventType(String type, String subtaskKey) {
        return switch (type) {
            case "execution_created", "task_started" -> ExecutionEventType.EXECUTION_STARTED;
            case "task_completed" -> ExecutionEventType.EXECUTION_COMPLETED;
            case "task_failed" -> ExecutionEventType.EXECUTION_FAILED;
            case "subtask_forked" -> ExecutionEventType.SUBTASK_CREATED;
            case "step_started" -> ExecutionEventType.SUBTASK_STARTED;
            case "subtask_completed" -> ExecutionEventType.SUBTASK_COMPLETED;
            case "error" -> subtaskKey == null
                    ? ExecutionEventType.EXECUTION_FAILED
                    : ExecutionEventType.SUBTASK_FAILED;
            default -> ExecutionEventType.TASK_STATUS_CHANGED;
        };
    }

    private static ExecutionEventStatus eventStatus(String type, String subtaskKey) {
        return switch (type) {
            case "execution_created" -> ExecutionEventStatus.DRAFT;
            case "task_completed" -> ExecutionEventStatus.COMPLETED;
            case "task_failed" -> ExecutionEventStatus.FAILED;
            case "error" -> subtaskKey == null
                    ? ExecutionEventStatus.FAILED
                    : ExecutionEventStatus.RUNNING;
            default -> ExecutionEventStatus.RUNNING;
        };
    }
}
