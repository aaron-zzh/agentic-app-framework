package com.xuejiai.aaf.module.ai.assistant.service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.assistant.application.AssistantCommand;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantVersion;
import com.xuejiai.aaf.framework.intelligent.assistant.model.CompletionCriteria;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantCommandPort;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.MemorySubject;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.SubjectKind;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ExecutionEventStatus;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AssistantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.CorrelationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.IdempotencyKey;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.RunId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;
import com.xuejiai.aaf.module.ai.assistant.port.ChannelAssistantExecutionPort;

/** 将外部渠道消息转换为 Assistant v2 命令，并同步聚合最终回复。 */
@Service
public class DefaultChannelAssistantExecutionAdapter implements ChannelAssistantExecutionPort {

    private static final Duration EXECUTION_TIMEOUT = Duration.ofMinutes(2);

    private final AssistantCommandPort assistants;

    public DefaultChannelAssistantExecutionAdapter(AssistantCommandPort assistants) {
        this.assistants = assistants;
    }

    @Override
    public String execute(Request request) {
        var tenantId = new TenantId(request.tenantId().toString());
        var userId = new UserId(request.ownerId().toString());
        var conversationKey =
                stable(
                        "channel-conversation",
                        tenantId.value(),
                        request.channelCode(),
                        request.bindingKey(),
                        request.externalUserId());
        var messageKey = stable("channel-message", conversationKey, request.sourceMessageId());
        var command =
                new AssistantCommand(
                        AssistantCommand.Operation.START,
                        tenantId,
                        userId,
                        new MemorySubject(
                                tenantId,
                                SubjectKind.VISITOR,
                                stable("channel-visitor", conversationKey)),
                        new AssistantId(request.assistantId()),
                        new AssistantVersion(request.assistantVersion()),
                        new ConversationId(stable("channel-conversation-id", conversationKey)),
                        new SessionId(stable("channel-session-id", conversationKey)),
                        new TaskId(stable("channel-task-id", messageKey)),
                        new ExecutionId(stable("channel-execution-id", messageKey)),
                        new RunId(stable("channel-run-id", messageKey)),
                        null,
                        new CorrelationId(stable("channel-correlation-id", conversationKey)),
                        null,
                        new IdempotencyKey(stable("channel-idempotency-key", messageKey)),
                        ControlMode.READ_ONLY,
                        null,
                        null,
                        0,
                        request.input(),
                        CompletionCriteria.responseDelivered(),
                        List.of(),
                        request.receivedAt());

        var events = assistants.execute(command).collectList().block(EXECUTION_TIMEOUT);
        if (events == null || events.isEmpty()) {
            throw new IllegalStateException("Assistant v2 未返回执行事件");
        }
        requireCompleted(events);
        return events.stream()
                .filter(event -> event.type() == ExecutionEventType.MESSAGE_COMPLETED)
                .map(event -> event.payload().values().get("text"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(text -> !text.isBlank())
                .reduce((first, second) -> second)
                .orElseThrow(() -> new IllegalStateException("Assistant v2 未返回最终文本"));
    }

    private static void requireCompleted(List<ExecutionEvent> events) {
        var failed =
                events.stream()
                        .anyMatch(
                                event ->
                                        event.type() == ExecutionEventType.EXECUTION_FAILED
                                                || event.type()
                                                        == ExecutionEventType.EXECUTION_CANCELED
                                                || event.type()
                                                        == ExecutionEventType.COMMAND_REJECTED
                                                || event.status() == ExecutionEventStatus.FAILED
                                                || event.status() == ExecutionEventStatus.CANCELED
                                                || event.status() == ExecutionEventStatus.REJECTED);
        if (failed) {
            throw new IllegalStateException("Assistant v2 渠道执行失败");
        }
        var completed =
                events.stream()
                        .anyMatch(
                                event ->
                                        event.type() == ExecutionEventType.EXECUTION_COMPLETED
                                                && event.status()
                                                        == ExecutionEventStatus.COMPLETED);
        if (!completed) {
            throw new IllegalStateException("Assistant v2 渠道执行未完成");
        }
    }

    private static String stable(String namespace, String... parts) {
        var canonical = new StringBuilder();
        append(canonical, namespace);
        for (var part : parts) {
            append(canonical, part);
        }
        return UUID.nameUUIDFromBytes(canonical.toString().getBytes(StandardCharsets.UTF_8))
                .toString();
    }

    private static void append(StringBuilder target, String value) {
        var normalized = value == null ? "" : value;
        target.append(normalized.length()).append(':').append(normalized).append(';');
    }
}
