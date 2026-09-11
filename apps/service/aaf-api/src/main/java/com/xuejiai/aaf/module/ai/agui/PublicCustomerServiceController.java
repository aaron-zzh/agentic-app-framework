package com.xuejiai.aaf.module.ai.agui;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.module.ai.agui.AssistantAguiController.RunMessage;
import com.xuejiai.aaf.module.ai.assistant.service.CustomerServiceExecutionService;
import com.xuejiai.aaf.module.ai.chat.service.CustomerServiceConversationService;
import com.xuejiai.aaf.module.ai.chat.service.CustomerServiceConversationService.PublicMessage;
import com.xuejiai.aaf.module.ai.chat.service.CustomerServiceVisitorTokenService;
import com.xuejiai.aaf.module.channel.service.WebCustomerServiceBindingResolver;
import com.xuejiai.aaf.module.channel.service.WebCustomerServiceBindingResolver.Binding;

import io.agentscope.core.agui.encoder.AguiEventEncoder;
import io.agentscope.core.agui.event.AguiEvent;
import tools.jackson.databind.JsonNode;

/** 官网匿名客服公开接口；登录身份不能调用。 */
@RestController
@RequestMapping("/api/public/customer-service")
@PreAuthorize("isAnonymous()")
public class PublicCustomerServiceController {

    private static final AguiEventEncoder ENCODER = new AguiEventEncoder();

    private final CustomerServiceVisitorTokenService visitorTokens;
    private final WebCustomerServiceBindingResolver bindings;
    private final CustomerServiceConversationService conversations;
    private final CustomerServiceExecutionService executions;
    private final AgUiProjector projector;

    public PublicCustomerServiceController(
            CustomerServiceVisitorTokenService visitorTokens,
            WebCustomerServiceBindingResolver bindings,
            CustomerServiceConversationService conversations,
            CustomerServiceExecutionService executions,
            AgUiProjector projector) {
        this.visitorTokens = visitorTokens;
        this.bindings = bindings;
        this.conversations = conversations;
        this.executions = executions;
        this.projector = projector;
    }

    @PostMapping("/session")
    public ResponseEntity<Result<SessionResponse>> session(
            @CookieValue(name = CustomerServiceVisitorTokenService.COOKIE_NAME, required = false)
                    String token) {
        var visitor = resolveVisitor(token);
        var binding = bindings.resolve();
        var session =
                inBindingOrg(
                        binding, () -> conversations.resolveOrCreate(visitor.subject(), binding));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, visitorTokens.cookie(visitor.token()).toString())
                .body(Result.success(new SessionResponse(session.threadId())));
    }

    @GetMapping("/session/{threadId}/messages")
    public Result<List<PublicMessage>> messages(
            @CookieValue(name = CustomerServiceVisitorTokenService.COOKIE_NAME) String token,
            @PathVariable String threadId) {
        var visitorSubject = visitorTokens.verify(token);
        var binding = bindings.resolve();
        var messages =
                inBindingOrg(
                        binding,
                        () -> conversations.listMessages(visitorSubject, threadId, binding));
        return Result.success(messages);
    }

    @PostMapping(
            value = "/run",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter run(
            @CookieValue(name = CustomerServiceVisitorTokenService.COOKIE_NAME) String token,
            @RequestBody PublicRunRequest request) {
        var visitorSubject = visitorTokens.verify(token);
        var binding = bindings.resolve();
        return inBindingOrg(binding, () -> startRun(visitorSubject, binding, request));
    }

    private SseEmitter startRun(String visitorSubject, Binding binding, PublicRunRequest request) {
        conversations.requireActive(visitorSubject, request.threadId(), binding);
        var input = lastUserText(request.messages());
        conversations.saveVisitorMessage(visitorSubject, request.threadId(), binding, input);
        var stream =
                executions.start(
                        binding, visitorSubject, request.threadId(), request.runId(), input);
        var emitter = new SseEmitter(600_000L);
        var session = projector.openSession();
        var buffers = new ConcurrentHashMap<String, StringBuilder>();
        stream.events()
                .subscribe(
                        event -> {
                            persistAssistantBlock(
                                    visitorSubject, request.threadId(), binding, event, buffers);
                            send(emitter, session.project(event), event.sequence());
                        },
                        failure -> {
                            send(
                                    emitter,
                                    session.fail(
                                            request.threadId(),
                                            stream.runId(),
                                            "CUSTOMER_SERVICE_STREAM_FAILED"),
                                    0);
                            emitter.complete();
                        },
                        () -> {
                            send(
                                    emitter,
                                    session.close(request.threadId(), stream.runId()),
                                    Long.MAX_VALUE);
                            emitter.complete();
                        });
        return emitter;
    }

    private void persistAssistantBlock(
            String visitorSubject,
            String threadId,
            Binding binding,
            ExecutionEvent event,
            Map<String, StringBuilder> buffers) {
        var values = event.payload().values();
        switch (event.type()) {
            case MESSAGE_DELTA -> {
                var messageId = blockMessageId(values);
                if (messageId == null) return;
                if (values.get("delta") instanceof String text) {
                    buffers.computeIfAbsent(messageId, ignored -> new StringBuilder()).append(text);
                }
            }
            case MESSAGE_BLOCK_COMPLETED -> {
                var messageId = blockMessageId(values);
                if (messageId == null) return;
                var buffer = buffers.remove(messageId);
                if (buffer == null || buffer.isEmpty()) return;
                conversations.saveAssistantMessage(
                        visitorSubject, threadId, binding, buffer.toString(), messageId);
            }
            default -> {}
        }
    }

    private CustomerServiceVisitorTokenService.VisitorToken resolveVisitor(String token) {
        if (token == null || token.isBlank()) {
            return visitorTokens.issueNew();
        }
        try {
            return visitorTokens.renew(visitorTokens.verify(token));
        } catch (BusinessException failure) {
            return visitorTokens.issueNew();
        }
    }

    private static String lastUserText(List<RunMessage> messages) {
        for (var index = messages.size() - 1; index >= 0; index--) {
            var message = messages.get(index);
            if ("user".equalsIgnoreCase(message.role())) {
                return textOnly(message.content());
            }
        }
        throw new IllegalArgumentException("messages 缺少 user 消息");
    }

    private static String textOnly(JsonNode content) {
        if (content == null || content.isNull()) {
            throw new IllegalArgumentException("最后一条 user 消息缺少文本");
        }
        if (content.isString()) {
            return requireText(content.asString());
        }
        if (!content.isArray()) {
            throw new IllegalArgumentException("匿名客服只接受文本消息");
        }
        var text = new StringBuilder();
        for (var part : content) {
            if (!"text".equals(part.path("type").asString())) {
                throw new IllegalArgumentException("匿名客服不接受附件或上下文");
            }
            var value = part.get("text");
            if (value == null || !value.isString()) {
                throw new IllegalArgumentException("文本消息格式无效");
            }
            if (!text.isEmpty()) {
                text.append('\n');
            }
            text.append(value.asString());
        }
        return requireText(text.toString());
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("最后一条 user 消息缺少文本");
        }
        return value.trim();
    }

    private static String blockMessageId(Map<String, Object> values) {
        if (!(values.get("replyId") instanceof String replyId)
                || !(values.get("blockId") instanceof String blockId)) {
            return null;
        }
        return replyId + ":" + blockId;
    }

    private static void send(SseEmitter emitter, List<AguiEvent> events, long cursor) {
        try {
            for (var index = 0; index < events.size(); index++) {
                emitter.send(
                        SseEmitter.event()
                                .id(cursor + "." + index)
                                .data(ENCODER.encodeToJson(events.get(index))));
            }
        } catch (IOException failure) {
            emitter.complete();
        }
    }

    private static <T> T inBindingOrg(Binding binding, Supplier<T> action) {
        OrgContext.clear();
        OrgContext.setCurrentOrgId(binding.orgId());
        try {
            return action.get();
        } finally {
            OrgContext.clear();
        }
    }

    public record PublicRunRequest(String threadId, String runId, List<RunMessage> messages) {
        public PublicRunRequest {
            threadId = requireText(threadId);
            runId = requireText(runId);
            messages = List.copyOf(Objects.requireNonNull(messages, "messages 不能为空"));
        }
    }

    public record SessionResponse(String threadId) {}
}
