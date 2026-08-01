package com.xuejiai.aaf.module.ai.agui;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.JsonNode;

import com.xuejiai.aaf.framework.intelligent.assistant.application.AssistantCommand;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantVersion;
import com.xuejiai.aaf.framework.intelligent.assistant.model.CompletionCriteria;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskModelSelection;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantCommandPort;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.MemorySubject;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.SubjectKind;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.*;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;

/** AG-UI 唯一入口；无固定 Assistant Bean、无 ThreadLocal、无 legacy fallback。 */
@RestController
@RequestMapping("/api/agui")
@PreAuthorize("isAuthenticated()")
public class AssistantAguiController {
    private final AssistantCommandPort assistants;
    private final OperatorContext operators;

    public AssistantAguiController(AssistantCommandPort assistants, OperatorContext operators) {
        this.assistants = assistants;
        this.operators = operators;
    }

    @PostMapping(
            value = "/run",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter run(@RequestBody RunRequest request) {
        var tenantId = tenant();
        var userId =
                new UserId(
                        operators
                                .currentOwnerId()
                                .map(String::valueOf)
                                .orElseThrow(() -> new AccessDeniedException("请求未认证")));
        var state = request.state();
        var assistantId = requireText(state, "assistantId");
        var assistantVersion = requireVersion(state);
        var taskModelSelection = taskModelSelection(state);
        var input = lastUserMessageText(request.messages());
        var parentRunId = normalizeOptional(request.parentRunId());
        var now = Instant.now();
        var command =
                new AssistantCommand(
                        AssistantCommand.Operation.START,
                        tenantId,
                        userId,
                        new MemorySubject(tenantId, SubjectKind.USER, userId.value()),
                        new AssistantId(assistantId),
                        assistantVersion,
                        new ConversationId(request.threadId()),
                        new SessionId(request.threadId()),
                        new TaskId("agui:" + request.runId()),
                        new ExecutionId(request.runId()),
                        new RunId(request.runId()),
                        parentRunId == null ? null : new ExecutionId(parentRunId),
                        new CorrelationId(request.threadId()),
                        parentRunId == null ? null : new CausationId(parentRunId),
                        new IdempotencyKey("agui:" + request.runId()),
                        ControlMode.READ_ONLY,
                        null,
                        null,
                        0,
                        input,
                        CompletionCriteria.responseDelivered(),
                        List.of(),
                        taskModelSelection,
                        now);
        var emitter = new SseEmitter(600_000L);
        assistants
                .execute(command)
                .subscribe(
                        event -> send(emitter, event),
                        emitter::completeWithError,
                        emitter::complete);
        return emitter;
    }

    private static void send(SseEmitter emitter, ExecutionEvent event) {
        try {
            emitter.send(
                    SseEmitter.event()
                            .id(Long.toString(event.sequence()))
                            .name(event.type().name())
                            .data(event, MediaType.APPLICATION_JSON));
        } catch (IOException failure) {
            emitter.completeWithError(failure);
        }
    }

    private static TenantId tenant() {
        var orgId = OrgContext.getCurrentOrgId();
        if (orgId == null) throw new AccessDeniedException("请求缺少组织上下文");
        return new TenantId(orgId.toString());
    }

    private static AssistantVersion requireVersion(JsonNode state) {
        var value = state.get("assistantVersion");
        if (value == null || !value.isIntegralNumber()) {
            throw new IllegalArgumentException("state.assistantVersion 必须是整数");
        }
        return new AssistantVersion(value.longValue());
    }

    private static TaskModelSelection taskModelSelection(JsonNode state) {
        var selection = requireObject(state.get("taskModelSelection"), "state.taskModelSelection");
        var modeValue = requireText(selection, "mode");
        final TaskModelSelection.Mode mode;
        try {
            mode = TaskModelSelection.Mode.valueOf(modeValue);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("不支持的任务模型选择模式: " + modeValue, failure);
        }
        var modelIdNode = selection.get("modelId");
        var modelId =
                modelIdNode == null || modelIdNode.isNull()
                        ? null
                        : requireText(selection, "modelId");
        return new TaskModelSelection(mode, modelId);
    }

    private static String lastUserMessageText(List<RunMessage> messages) {
        for (var index = messages.size() - 1; index >= 0; index--) {
            var message = messages.get(index);
            if (!"user".equalsIgnoreCase(message.role())) {
                continue;
            }
            var text = messageText(message.content());
            if (text.isBlank()) {
                throw new IllegalArgumentException("最后一条 user 消息没有文本内容");
            }
            return text;
        }
        throw new IllegalArgumentException("messages 缺少 user 消息");
    }

    private static String messageText(JsonNode content) {
        if (content == null || content.isNull()) {
            return "";
        }
        if (content.isTextual()) {
            return content.textValue();
        }
        if (!content.isArray()) {
            return "";
        }
        var text = new StringBuilder();
        for (var part : content) {
            var partText = part.get("text");
            if (!"text".equals(part.path("type").asText())
                    || partText == null
                    || !partText.isTextual()) {
                continue;
            }
            if (!text.isEmpty()) {
                text.append('\n');
            }
            text.append(partText.textValue());
        }
        return text.toString();
    }

    private static JsonNode requireObject(JsonNode value, String field) {
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException(field + " 必须是对象");
        }
        return value;
    }

    private static String requireText(JsonNode object, String field) {
        var value = object.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return value.textValue().trim();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record RunRequest(
            String threadId,
            String runId,
            String parentRunId,
            JsonNode state,
            List<RunMessage> messages) {
        public RunRequest {
            threadId = requireText(threadId, "threadId");
            runId = requireText(runId, "runId");
            state = requireObject(state, "state");
            messages = List.copyOf(Objects.requireNonNull(messages, "messages 不能为空"));
        }
    }

    public record RunMessage(String id, String role, JsonNode content) {
        public RunMessage {
            role = requireText(role, "message.role");
        }
    }
}
