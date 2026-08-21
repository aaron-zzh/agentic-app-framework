package com.xuejiai.aaf.module.ai.agui;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.module.ai.assistant.service.AssistantExecutionService;
import com.xuejiai.aaf.module.ai.assistant.service.AssistantExecutionService.ExecutionStream;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.ActionAuthorizationPolicy;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.ArtifactPersistence;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.ClarificationPolicy;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.ExecutionOptions;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.Input;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.InteractionMode;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.KnowledgeMode;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.KnowledgeOptions;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.MemoryMode;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.MemoryOptions;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.ModelMode;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.ModelSelection;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.OutputOptions;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.RouteConstraint;
import com.xuejiai.aaf.module.ai.chat.agui.AgUiEvent;
import com.xuejiai.aaf.module.ai.chat.service.ChatService;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import tools.jackson.databind.JsonNode;

/** AG-UI 唯一入口；无固定 Assistant Bean、无 ThreadLocal、无 legacy fallback。 */
@RestController
@RequestMapping("/api/agui")
@PreAuthorize("isAuthenticated()")
public class AssistantAguiController {
    private final AssistantExecutionService assistantExecutions;
    private final ChatService chatService;
    private final AgUiProjector agUiProjector;
    private final Validator validator;

    public AssistantAguiController(
            AssistantExecutionService assistantExecutions,
            ChatService chatService,
            AgUiProjector agUiProjector,
            Validator validator) {
        this.assistantExecutions = assistantExecutions;
        this.chatService = chatService;
        this.agUiProjector = agUiProjector;
        this.validator = validator;
    }

    @PostMapping(
            value = "/run",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter run(@RequestBody RunRequest request) {
        chatService.requireOwnedAiThread(request.threadId());
        var stream = executionStream(request);
        var emitter = new SseEmitter(600_000L);
        stream.events()
                .subscribe(
                        event -> send(emitter, agUiProjector.project(event), event.sequence()),
                        failure -> sendError(emitter, request.runId()),
                        emitter::complete);
        return emitter;
    }

    private ExecutionStream executionStream(RunRequest request) {
        var input = lastUserMessageText(request.messages());
        final Mode mode;
        try {
            mode = Mode.valueOf(requireText(request.state(), "mode"));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("state.mode 仅支持 CHAT、EXECUTION 或 TEAM", failure);
        }
        return switch (mode) {
            case CHAT ->
                    assistantExecutions.start(
                            chatRequest(request.state(), input),
                            request.threadId(),
                            request.runId());
            case EXECUTION ->
                    assistantExecutions.start(
                            executionRequest(request.state(), input),
                            request.threadId(),
                            request.runId());
            case TEAM ->
                    assistantExecutions.startTeam(
                            executionRequest(request.state(), input),
                            requireText(request.state(), "teamId"),
                            requirePositiveLong(request.state(), "teamVersion"),
                            request.threadId(),
                            request.runId());
        };
    }

    private AssistantExecutionRequest executionRequest(JsonNode state, String input) {
        var requestNode = requireObject(state.get("request"), "state.request");
        final AssistantExecutionRequest executionRequest;
        try {
            executionRequest = JsonUtils.convertValue(requestNode, AssistantExecutionRequest.class);
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException(
                    "state.request 不是有效的 AssistantExecutionRequest", failure);
        }
        if (executionRequest == null) {
            throw new IllegalArgumentException("state.request 不能为空");
        }
        var violations = validator.validate(executionRequest);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        if (!input.equals(executionRequest.input().text())) {
            throw new IllegalArgumentException("state.request.input.text 必须与最后一条 user 消息一致");
        }
        return executionRequest;
    }

    private static AssistantExecutionRequest chatRequest(JsonNode state, String input) {
        var assistantNode = state.get("assistantId");
        var assistant =
                assistantNode == null || assistantNode.isNull()
                        ? null
                        : new AssistantExecutionRequest.AssistantTarget(
                                requireText(state, "assistantId"));
        return new AssistantExecutionRequest(
                assistant,
                new ExecutionOptions(
                        InteractionMode.CONVERSATIONAL,
                        RouteConstraint.AUTO,
                        ClarificationPolicy.MINIMAL,
                        ActionAuthorizationPolicy.DENY_AUTHORIZED_ACTIONS,
                        ArtifactPersistence.RETURN_ONLY),
                new Input(input, Map.of(), List.of()),
                null,
                null,
                new KnowledgeOptions(KnowledgeMode.DEFAULT, Set.of(), 5, 0.2),
                modelSelection(state),
                new MemoryOptions(MemoryMode.DEFAULT),
                new OutputOptions(null, null, null));
    }

    private static ModelSelection modelSelection(JsonNode state) {
        var selection = requireObject(state.get("taskModelSelection"), "state.taskModelSelection");
        var modeValue = requireText(selection, "mode");
        final ModelMode mode;
        try {
            mode = ModelMode.valueOf(modeValue);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("不支持的任务模型选择模式: " + modeValue, failure);
        }
        var modelIdNode = selection.get("modelId");
        var modelId =
                modelIdNode == null || modelIdNode.isNull()
                        ? null
                        : requireText(selection, "modelId");
        return new ModelSelection(mode, modelId);
    }

    private static void send(SseEmitter emitter, List<AgUiEvent> events, long cursor) {
        try {
            for (var event : events) {
                emitter.send(
                        SseEmitter.event()
                                .id(Long.toString(cursor))
                                .name(event.type())
                                .data(event.toMap(), MediaType.APPLICATION_JSON));
            }
        } catch (IOException failure) {
            emitter.complete();
        }
    }

    private static void sendError(SseEmitter emitter, String runId) {
        try {
            var event = AgUiEvent.runError(runId, "Assistant 运行未完成");
            emitter.send(
                    SseEmitter.event()
                            .name(event.type())
                            .data(event.toMap(), MediaType.APPLICATION_JSON));
        } catch (IOException failure) {
            // 客户端已断开时无需继续发送。
        } finally {
            emitter.complete();
        }
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
        if (content.isString()) {
            return content.textValue();
        }
        if (!content.isArray()) {
            return "";
        }
        var text = new StringBuilder();
        for (var part : content) {
            var partText = part.get("text");
            if (!"text".equals(part.path("type").asString())
                    || partText == null
                    || !partText.isString()) {
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
        if (value == null || !value.isString() || value.textValue().isBlank()) {
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

    private static long requirePositiveLong(JsonNode object, String field) {
        var value = object.get(field);
        if (value == null || !value.isIntegralNumber() || value.longValue() < 1) {
            throw new IllegalArgumentException(field + " 必须是正整数");
        }
        return value.longValue();
    }

    private enum Mode {
        CHAT,
        EXECUTION,
        TEAM
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
