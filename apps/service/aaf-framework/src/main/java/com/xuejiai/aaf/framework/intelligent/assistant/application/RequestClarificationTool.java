package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.xuejiai.aaf.framework.intelligent.agent.port.ContextAwareToolHandler;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocation;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocationResult;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ClarificationRequest;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ClarificationRequest.Question;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ClarificationRequiredException;
import com.xuejiai.aaf.framework.intelligent.assistant.model.HitlTransition.ClarificationRequestTransition;
import com.xuejiai.aaf.framework.intelligent.assistant.port.HitlTransitionPort;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ExecutionEventStatus;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.OwnerType;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventPayload;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.EventId;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** TaskNode 创建 canonical structured clarification 的唯一模型入口。 */
public final class RequestClarificationTool implements ContextAwareToolHandler {

    public static final String TOOL_NAME = "request_clarification";
    private static final Duration RESPONSE_WINDOW = Duration.ofDays(30);
    private static final int MAX_QUESTIONS = 20;

    private final HitlTransitionPort transitions;
    private final Clock clock;

    public RequestClarificationTool(HitlTransitionPort transitions, Clock clock) {
        this.transitions = Objects.requireNonNull(transitions, "transitions 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    @Override
    public String toolName() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return "当当前 TaskNode 必须精确补齐一个或多个字段后才能继续时，创建可恢复的结构化澄清。"
                + "普通对话追问不得调用；DIRECT execution 必须先提升为 Task。";
    }

    /** 只改变 canonical Task 控制状态，不执行外部业务动作或扩大授权。 */
    @Override
    public boolean readOnly() {
        return true;
    }

    @Override
    public Map<String, Object> inputSchema() {
        var questionSchema =
                Map.of(
                        "type",
                        "object",
                        "properties",
                        Map.of(
                                "field",
                                Map.of("type", "string", "description", "待补齐字段的稳定名称"),
                                "question",
                                Map.of("type", "string", "description", "向用户展示的问题"),
                                "options",
                                Map.of(
                                        "type",
                                        "array",
                                        "description",
                                        "可选的单选值；空数组表示自由文本",
                                        "items",
                                        Map.of("type", "string"))),
                        "required",
                        List.of("field", "question"));
        return Map.of(
                "type",
                "object",
                "properties",
                Map.of(
                        "questions",
                        Map.of(
                                "type",
                                "array",
                                "minItems",
                                1,
                                "maxItems",
                                MAX_QUESTIONS,
                                "items",
                                questionSchema)),
                "required",
                List.of("questions"));
    }

    @Override
    public Mono<ToolInvocationResult> invoke(ToolInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation 不能为空");
        return Mono.fromCallable(() -> request(invocation))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private ToolInvocationResult request(ToolInvocation invocation) {
        var context = invocation.context();
        if (context.taskId() == null || context.nodeIdentity() == null) {
            throw new IllegalStateException(
                    "request_clarification 只能在 canonical TaskNode 内调用；DIRECT 必须先 promotion");
        }
        var questions = decodeQuestions(invocation.arguments());
        var requiredFields = questions.stream().map(Question::field).toList();
        var createdAt = clock.instant();
        var requestId = UUID.randomUUID().toString().replace("-", "");
        var request =
                new ClarificationRequest(
                        requestId,
                        context.taskId(),
                        context.executionId(),
                        context.nodeIdentity().nodeId(),
                        requiredFields,
                        questions,
                        createdAt.plus(RESPONSE_WINDOW),
                        ClarificationRequest.Status.PENDING,
                        Map.of(),
                        createdAt,
                        null,
                        null);
        transitions.requestClarification(
                new ClarificationRequestTransition(
                        context, request, clarificationRequestEvent(context, request)));
        throw new ClarificationRequiredException(requestId, "任务需要用户补充结构化信息");
    }

    private static ExecutionEvent clarificationRequestEvent(
            com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext context,
            ClarificationRequest request) {
        var questionPayload =
                request.questions().stream()
                        .map(
                                question -> {
                                    var values = new LinkedHashMap<String, Object>();
                                    values.put("field", question.field());
                                    values.put("question", question.question());
                                    values.put("options", question.options());
                                    return Map.copyOf(values);
                                })
                        .toList();
        var payload = new LinkedHashMap<String, Object>();
        payload.put("requestId", request.requestId());
        payload.put("requiredFields", request.requiredFields());
        payload.put("questions", questionPayload);
        payload.put("deadline", request.deadline().toString());
        return new ExecutionEvent(
                new EventId("clarification-request-" + request.requestId()),
                context.tenantId(),
                context.conversationId(),
                context.sessionId(),
                context.taskId(),
                context.executionId(),
                context.runId(),
                context.parentExecutionId(),
                1,
                ExecutionEventType.CLARIFICATION_REQUESTED,
                ExecutionEventStatus.AWAITING_CLARIFICATION,
                context.controlMode(),
                OwnerType.ASSISTANT,
                context.assistantId(),
                null,
                context.userId(),
                context.correlationId(),
                context.causationId(),
                context.idempotencyKey(),
                new ExecutionEventPayload(payload),
                request.createdAt(),
                context.nodeIdentity());
    }

    private static List<Question> decodeQuestions(Map<String, Object> arguments) {
        var raw = arguments.get("questions");
        if (!(raw instanceof List<?> entries)
                || entries.isEmpty()
                || entries.size() > MAX_QUESTIONS) {
            throw new IllegalArgumentException("questions 数量必须在 1.." + MAX_QUESTIONS);
        }
        var questions = new ArrayList<Question>(entries.size());
        for (var entry : entries) {
            if (!(entry instanceof Map<?, ?> values)) {
                throw new IllegalArgumentException("questions 的每一项必须是对象");
            }
            var field = requireText(values.get("field"), "question.field", 64);
            var question = requireText(values.get("question"), "question.question", 500);
            questions.add(new Question(field, question, decodeOptions(values.get("options"))));
        }
        return List.copyOf(questions);
    }

    private static List<String> decodeOptions(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> entries) || entries.size() > 20) {
            throw new IllegalArgumentException("question.options 必须是最多 20 项的数组");
        }
        return entries.stream().map(value -> requireText(value, "question.option", 200)).toList();
    }

    private static String requireText(Object value, String field, int maxLength) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        var normalized = text.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " 不能超过 " + maxLength + " 字符");
        }
        return normalized;
    }
}
