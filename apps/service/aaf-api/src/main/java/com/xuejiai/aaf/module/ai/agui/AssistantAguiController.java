package com.xuejiai.aaf.module.ai.agui;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.assistant.model.HumanApproval;
import com.xuejiai.aaf.framework.intelligent.assistant.port.HitlCoordinatorPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.HumanApprovalPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskRecoveryDispatchPort;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.RunId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.assistant.AssistantErrorCode;
import com.xuejiai.aaf.module.ai.assistant.document.DocumentAttachmentGuardService;
import com.xuejiai.aaf.module.ai.assistant.document.DocumentTokenBudgetSplitter;
import com.xuejiai.aaf.module.ai.assistant.service.AssistantApprovalEventService;
import com.xuejiai.aaf.module.ai.assistant.service.AssistantExecutionService;
import com.xuejiai.aaf.module.ai.assistant.service.AssistantExecutionService.ExecutionStream;
import com.xuejiai.aaf.module.ai.assistant.service.AssistantExecutionService.TeamTarget;
import com.xuejiai.aaf.module.ai.assistant.service.ClarificationResumeService;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.ActionAuthorizationPolicy;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.ArtifactPersistence;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.Attachment;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.AttachmentType;
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
import com.xuejiai.aaf.module.ai.chat.service.ChatService;

import io.agentscope.core.agui.encoder.AguiEventEncoder;
import io.agentscope.core.agui.event.AguiEvent;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

/** AG-UI 唯一入口；无固定 Assistant Bean、无 ThreadLocal、无 legacy fallback。 */
@Slf4j
@RestController
@RequestMapping("/api/agui")
@PreAuthorize("isAuthenticated()")
public class AssistantAguiController {
    /** 线程安全且无状态，可跨请求共享。 */
    private static final AguiEventEncoder ENCODER = new AguiEventEncoder();

    private final AssistantExecutionService assistantExecutions;
    private final ChatService chatService;
    private final AgUiProjector agUiProjector;
    private final Validator validator;
    private final HitlCoordinatorPort hitl;
    private final HumanApprovalPort approvals;
    private final TaskRecoveryDispatchPort recoveries;
    private final AssistantApprovalEventService approvalEvents;
    private final OperatorContext operatorContext;
    private final ClarificationResumeService clarificationResume;
    private final DocumentAttachmentGuardService documentAttachmentGuard;
    private final DocumentTokenBudgetSplitter documentTokenBudgetSplitter;

    public AssistantAguiController(
            AssistantExecutionService assistantExecutions,
            ChatService chatService,
            AgUiProjector agUiProjector,
            Validator validator,
            HitlCoordinatorPort hitl,
            HumanApprovalPort approvals,
            TaskRecoveryDispatchPort recoveries,
            AssistantApprovalEventService approvalEvents,
            OperatorContext operatorContext,
            ClarificationResumeService clarificationResume,
            DocumentAttachmentGuardService documentAttachmentGuard,
            DocumentTokenBudgetSplitter documentTokenBudgetSplitter) {
        this.assistantExecutions = assistantExecutions;
        this.chatService = chatService;
        this.agUiProjector = agUiProjector;
        this.validator = validator;
        this.hitl = hitl;
        this.approvals = approvals;
        this.recoveries = recoveries;
        this.approvalEvents = approvalEvents;
        this.operatorContext = operatorContext;
        this.clarificationResume = clarificationResume;
        this.documentAttachmentGuard = documentAttachmentGuard;
        this.documentTokenBudgetSplitter = documentTokenBudgetSplitter;
    }

    @PostMapping(
            value = "/run",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter run(@RequestBody RunRequest request) {
        chatService.requireOwnedAiThread(request.threadId());
        return request.resume().isEmpty() ? startRun(request) : resumeRun(request);
    }

    /** 新建 execution：无 {@code resume}，走既有逻辑。 */
    private SseEmitter startRun(RunRequest request) {
        var stream = executionStream(request);
        var emitter = new SseEmitter(600_000L);
        var session = agUiProjector.openSession();
        var threadId = request.threadId();
        var runId = request.runId();
        persistUserMessage(threadId, request.messages());
        // AAF-114 #11412：按 blockMessageId（replyId:blockId，与前端 assistant-ui ThreadMessage.id 完全一致）
        // 累积文本增量，在 MESSAGE_BLOCK_COMPLETED 时机落库，payload 记录该 id 供反馈接口精确关联。
        // 每个 run 独立一份，随 SSE 订阅生命周期存在，无需考虑跨请求并发。
        var blockTextBuffers = new ConcurrentHashMap<String, StringBuilder>();
        stream.events()
                .subscribe(
                        event -> {
                            persistAssistantMessageOnBlockCompleted(
                                    threadId, event, blockTextBuffers);
                            send(emitter, session.project(event), event.sequence());
                        },
                        failure -> {
                            // 事件流以异常终止时补 RUN_ERROR + RUN_FINISHED，保证 run 在协议上闭合
                            send(
                                    emitter,
                                    session.fail(threadId, runId, "ASSISTANT_STREAM_FAILED"),
                                    0);
                            emitter.complete();
                        },
                        () -> {
                            // 兜底闭合未配对的 message/toolCall 与未终结的 run
                            send(emitter, session.close(threadId, runId), Long.MAX_VALUE);
                            emitter.complete();
                        });
        return emitter;
    }

    /**
     * 恢复 execution（AAF-104 #10404，AAF-114 #11408 第二版扩展）：{@code resume} 非空时不新建 execution，按 {@code
     * interruptId} 归属分派到 approval 或 Clarification 处理分支——两者共用同一套 AG-UI interrupt/resume
     * 传输协议，但领域模型（{@code HumanApproval} vs {@code ClarificationRequest}） 完全独立，不合并成同一状态机。
     *
     * <p>归属判定：先查 {@code approvals.find}；命中则是审批恢复（{@link #resumeApproval}，原有逻辑不变）。 查不到则视为
     * Clarification 恢复（{@link #resumeClarification}）——{@code approvalId} 与 {@code requestId} 分属不同
     * ID 空间，不会误判。
     *
     * <p>仅处理 {@code resume} 的第一条：AAF 当前每次 interrupt 只对应一个 {@code interruptId}，暂无真实的
     * 批量场景（协议允许数组，实现先满足单条，为未来扩展留出空间）。
     */
    private SseEmitter resumeRun(RunRequest request) {
        var entry = request.resume().getFirst();
        var tenantId = currentTenant();
        var approval = approvals.find(tenantId, entry.interruptId());
        if (approval.isPresent()) {
            return resumeApproval(request, entry, tenantId);
        }
        return resumeClarification(request, entry, tenantId);
    }

    /**
     * 审批恢复（AAF-104 #10404，原 {@code resumeRun} 逻辑原样保留）：逐条按 {@code interruptId}（即 {@code
     * approvalId}）调用 {@link HitlCoordinatorPort#decide} 落定决定。批准时触发 {@link
     * TaskRecoveryDispatchPort#recover} 驱动真正的续接执行（{@code AssistantApplicationService} 的 {@code
     * Operation.RESUME} 分支，沿用原 {@code executionId} 续接 core 对话历史，AAF-110 已实现）， 再用 {@link
     * AssistantApprovalEventService#stream} 续读已持久化事件（不占用执行线程等待恢复）。
     *
     * <p>拒绝决定不驱动恢复：{@link AssistantApprovalEventService#stream} 要求 {@code approval.status() ==
     * APPROVED}，拒绝后任务转为 {@code PAUSED}（非终态，不会产生新的可续读事件）， 因此拒绝分支直接闭合本次 run，不调用 {@code stream}。
     */
    private SseEmitter resumeApproval(RunRequest request, ResumeEntry entry, TenantId tenantId) {
        hitl.decide(
                new HitlCoordinatorPort.DecisionCommand(
                        tenantId,
                        entry.interruptId(),
                        entry.approved()
                                ? HumanApproval.Status.APPROVED
                                : HumanApproval.Status.REJECTED,
                        currentUser(),
                        entry.status(),
                        Instant.now()));
        var approval =
                approvals
                        .find(tenantId, entry.interruptId())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "未知的 interruptId: " + entry.interruptId()));
        var emitter = new SseEmitter(600_000L);
        var session = agUiProjector.openSession();
        var threadId = request.threadId();
        var runId = request.runId();
        if (approval.status() != HumanApproval.Status.APPROVED) {
            // 拒绝决定已落定，任务转为 PAUSED（非终态事件），不驱动恢复执行，无新事件可续读——
            // 直接闭合本次 run，AUTHORIZATION_DENIED 会在下次用户交互产生的事件流中自然呈现。
            send(emitter, session.close(threadId, runId), Long.MAX_VALUE);
            emitter.complete();
            return emitter;
        }
        recoveries.recover(tenantId, entry.interruptId());
        approvalEvents.stream(approval)
                .events()
                .subscribe(
                        stored ->
                                send(
                                        emitter,
                                        session.project(stored.event()),
                                        stored.eventOffset()),
                        failure -> {
                            send(
                                    emitter,
                                    session.fail(threadId, runId, "ASSISTANT_STREAM_FAILED"),
                                    0);
                            emitter.complete();
                        },
                        () -> {
                            send(emitter, session.close(threadId, runId), Long.MAX_VALUE);
                            emitter.complete();
                        });
        return emitter;
    }

    /**
     * Clarification 恢复（AAF-114 #11408 第二版）：{@code interruptId} 即 {@code
     * ClarificationRequest.requestId}。取消（{@code status="cancelled"}）场景当前无独立取消端口方法，直接闭合本次
     * run，不驱动状态转换——用户可通过下一轮自然语言输入触发既有 MODIFY/UNRELATED 分类路径。
     *
     * <p>{@code TaskCommandService#acceptInput} 是 {@code Mono}；成功后才订阅事件续读，避免在写入尚未落地时 提前查询导致漏读第一批事件。
     */
    private SseEmitter resumeClarification(
            RunRequest request, ResumeEntry entry, TenantId tenantId) {
        var clarification =
                clarificationResume
                        .findByRequestId(tenantId, entry.interruptId())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "未知的 interruptId: " + entry.interruptId()));
        var emitter = new SseEmitter(600_000L);
        var session = agUiProjector.openSession();
        var threadId = request.threadId();
        var runId = request.runId();
        if (!entry.resolved()) {
            send(emitter, session.close(threadId, runId), Long.MAX_VALUE);
            emitter.complete();
            return emitter;
        }
        var resumedAt = Instant.now();
        clarificationResume
                .submit(tenantId, currentUserId(), clarification, entry.payload())
                .thenMany(
                        approvalEvents
                                .streamByExecutionId(
                                        tenantId,
                                        clarification.taskId(),
                                        clarification.executionId(),
                                        new RunId(runId),
                                        resumedAt)
                                .events())
                .subscribe(
                        stored ->
                                send(
                                        emitter,
                                        session.project(stored.event()),
                                        stored.eventOffset()),
                        failure -> {
                            send(
                                    emitter,
                                    session.fail(threadId, runId, "ASSISTANT_STREAM_FAILED"),
                                    0);
                            emitter.complete();
                        },
                        () -> {
                            send(emitter, session.close(threadId, runId), Long.MAX_VALUE);
                            emitter.complete();
                        });
        return emitter;
    }

    private TenantId currentTenant() {
        var orgId = OrgContext.getCurrentOrgId();
        if (orgId == null) {
            throw new AccessDeniedException("请求缺少已校验的组织上下文");
        }
        return new TenantId(orgId.toString());
    }

    private String currentUser() {
        return operatorContext
                .currentOwnerId()
                .map(String::valueOf)
                .orElseThrow(() -> new AccessDeniedException("请求未认证"));
    }

    private UserId currentUserId() {
        return new UserId(currentUser());
    }

    private ExecutionStream executionStream(RunRequest request) {
        var input = lastUserInput(request.messages());
        var props = request.forwardedProps();
        var plan = mode(props).plan(props, input, validator);
        return assistantExecutions.start(
                plan.request(), plan.team(), request.threadId(), request.runId());
    }

    /** 运行模式来自 {@code forwardedProps.mode}——它是本次 run 的调用参数，不是线程共享状态。 */
    private static Mode mode(JsonNode props) {
        try {
            return Mode.valueOf(requireText(props, "mode", "forwardedProps.mode"));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException(
                    "forwardedProps.mode 仅支持 CHAT、EXECUTION 或 TEAM", failure);
        }
    }

    private static AssistantExecutionRequest executionRequest(
            JsonNode props, String input, Validator validator) {
        var requestNode = requireObject(props.get("request"), "forwardedProps.request");
        final AssistantExecutionRequest executionRequest;
        try {
            executionRequest = JsonUtils.convertValue(requestNode, AssistantExecutionRequest.class);
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException(
                    "forwardedProps.request 不是有效的 AssistantExecutionRequest", failure);
        }
        if (executionRequest == null) {
            throw new IllegalArgumentException("forwardedProps.request 不能为空");
        }
        var violations = validator.validate(executionRequest);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        if (!input.equals(executionRequest.input().text())) {
            throw new IllegalArgumentException(
                    "forwardedProps.request.input.text 必须与最后一条 user 消息一致");
        }
        return executionRequest;
    }

    private static AssistantExecutionRequest chatRequest(JsonNode props, UserInput input) {
        var assistantNode = props.get("assistantId");
        var assistant =
                assistantNode == null || assistantNode.isNull()
                        ? null
                        : new AssistantExecutionRequest.AssistantTarget(
                                requireText(props, "assistantId", "forwardedProps.assistantId"));
        var role = chatRoleSelection(props);
        var skill = chatSkillSelection(props);
        return new AssistantExecutionRequest(
                assistant,
                new ExecutionOptions(
                        InteractionMode.CONVERSATIONAL,
                        role == null && skill == null
                                ? RouteConstraint.AUTO
                                : RouteConstraint.FIXED,
                        ClarificationPolicy.MINIMAL,
                        ActionAuthorizationPolicy.DENY_AUTHORIZED_ACTIONS,
                        ArtifactPersistence.RETURN_ONLY),
                new Input(input.text(), Map.of(), input.attachments()),
                role,
                skill,
                new KnowledgeOptions(KnowledgeMode.DEFAULT, Set.of(), 5, 0.2),
                modelSelection(props),
                new MemoryOptions(MemoryMode.DEFAULT),
                new OutputOptions(null, null, null));
    }

    /**
     * CHAT 模式可选 Role 显式指定（AAF-107 #10708）：与 {@code EXECUTION} 模式的 {@code
     * forwardedProps.request.role} 同一形状，但省略时不报错——CHAT 允许省略走 AUTO 动态决策， {@code EXECUTION}
     * 模式的显式协议校验（{@link Validator}）不适用于此。
     */
    private static AssistantExecutionRequest.RoleSelection chatRoleSelection(JsonNode props) {
        var node = props.get("role");
        if (node == null || node.isNull()) {
            return null;
        }
        return new AssistantExecutionRequest.RoleSelection(
                requireText(
                        requireObject(node, "forwardedProps.role"),
                        "key",
                        "forwardedProps.role.key"));
    }

    /** CHAT 模式可选 Skill 显式指定（AAF-107 #10708），与 {@link #chatRoleSelection} 同一模式。 */
    private static AssistantExecutionRequest.SkillSelection chatSkillSelection(JsonNode props) {
        var node = props.get("skill");
        if (node == null || node.isNull()) {
            return null;
        }
        return new AssistantExecutionRequest.SkillSelection(
                requireText(
                        requireObject(node, "forwardedProps.skill"),
                        "code",
                        "forwardedProps.skill.code"));
    }

    private static ModelSelection modelSelection(JsonNode props) {
        var selection =
                requireObject(props.get("taskModelSelection"), "forwardedProps.taskModelSelection");
        var modeValue = requireText(selection, "mode", "forwardedProps.taskModelSelection.mode");
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
                        : requireText(
                                selection, "modelId", "forwardedProps.taskModelSelection.modelId");
        return new ModelSelection(mode, modelId);
    }

    /**
     * 持久化本次 run 的用户消息（AAF-114 #11411）。
     *
     * <p>只在 {@code startRun}（新建 execution）调用；{@code resumeRun} 是审批恢复续跑，不是新用户输入，不重复持久化。
     * 失败仅记录警告，不阻断执行——历史持久化是执行链路的旁路能力，不应影响核心对话可用性。
     */
    private void persistUserMessage(String threadId, List<RunMessage> messages) {
        try {
            var input = lastUserInput(messages);
            if (!input.text().isBlank()) {
                chatService.saveMessageByThreadId(threadId, null, "HUMAN", "user", input.text());
            }
        } catch (RuntimeException failure) {
            log.warn("用户消息持久化失败 threadId={}: {}", threadId, failure.getMessage());
        }
    }

    /**
     * 累积 {@code MESSAGE_DELTA} 文本，在 {@code MESSAGE_BLOCK_COMPLETED} 时落库（AAF-114 #11412）。
     *
     * <p>此前（#11411）在整次回复结束（{@code MESSAGE_COMPLETED}，对应 AgentScope {@code AGENT_RESULT}）时一次性
     * 持久化，但该事件 payload 的 {@code messageId} 取自 AgentScope {@code Msg.id}（构造时随机 UUID），与前端
     * assistant-ui {@code ThreadMessage.id}（{@code replyId:blockId}，见 {@code
     * TextMessageEventConverter}） 是两个独立生成的值，无法用于反馈等需要精确关联前端可见消息的场景。改为在块级持久化：{@code MESSAGE_DELTA}
     * 时按 {@code replyId:blockId} 累积文本，{@code MESSAGE_BLOCK_COMPLETED} 时落库并把该 id 写入 {@code
     * ConversationMessage.payload}，与前端 messageId 精确对应。一次回复可能有多个文本块，各自独立持久化一条消息， 与 AG-UI
     * 协议"一个块即一条独立消息"的语义一致。
     */
    private void persistAssistantMessageOnBlockCompleted(
            String threadId, ExecutionEvent event, Map<String, StringBuilder> blockTextBuffers) {
        var values = event.payload().values();
        switch (event.type()) {
            case MESSAGE_DELTA -> {
                var blockMessageId = blockMessageId(values);
                if (blockMessageId == null) return;
                var delta = values.get("delta");
                if (delta instanceof String text) {
                    blockTextBuffers
                            .computeIfAbsent(blockMessageId, id -> new StringBuilder())
                            .append(text);
                }
            }
            case MESSAGE_BLOCK_COMPLETED -> {
                var blockMessageId = blockMessageId(values);
                if (blockMessageId == null) return;
                var buffer = blockTextBuffers.remove(blockMessageId);
                if (buffer == null || buffer.isEmpty()) return;
                try {
                    chatService.saveMessageByThreadId(
                            threadId, null, "AI", "assistant", buffer.toString(), blockMessageId);
                } catch (RuntimeException failure) {
                    log.warn(
                            "AI 回复持久化失败 threadId={}, messageId={}: {}",
                            threadId,
                            blockMessageId,
                            failure.getMessage());
                }
            }
            default -> {}
        }
    }

    /** {@code replyId:blockId} 派生，与 {@code TextMessageEventConverter.blockMessageId} 保持一致的拼接规则。 */
    private static String blockMessageId(Map<String, Object> values) {
        if (!(values.get("replyId") instanceof String replyId)
                || !(values.get("blockId") instanceof String blockId)) {
            return null;
        }
        return replyId + ":" + blockId;
    }

    /**
     * 发送一批 AG-UI 事件。
     *
     * <p>序列化必须走 {@link AguiEventEncoder}：官方 {@code AguiEvent} 用 Jackson 2 注解，Spring Boot 4 的
     * Jackson 3 {@code HttpMessageConverter} 不认这些注解。encoder 自带 Jackson 2 codec 且返回带前导 空格的 JSON，与
     * SSE 的 {@code data:} 前缀拼成标准 {@code data: {...}}。
     *
     * <p>SSE id 按「事件游标.批内序号」生成：一条 ExecutionEvent 可投影出多条 AG-UI 事件，复用同一 id 会 让 Last-Event-ID 断线续传错位。
     */
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

    private UserInput lastUserInput(List<RunMessage> messages) {
        for (var index = messages.size() - 1; index >= 0; index--) {
            var message = messages.get(index);
            if (!"user".equalsIgnoreCase(message.role())) {
                continue;
            }
            var input = messageInput(message.content());
            if (input.text().isBlank() && input.attachments().isEmpty()) {
                throw new IllegalArgumentException("最后一条 user 消息没有文本或图片内容");
            }
            if (input.text().isBlank()) {
                return new UserInput("请分析用户提供的图片。", input.attachments());
            }
            return input;
        }
        throw new IllegalArgumentException("messages 缺少 user 消息");
    }

    private UserInput messageInput(JsonNode content) {
        if (content == null || content.isNull()) {
            return new UserInput("", List.of());
        }
        if (content.isString()) {
            return new UserInput(content.asString(), List.of());
        }
        if (!content.isArray()) {
            return new UserInput("", List.of());
        }
        var text = new StringBuilder();
        var attachments = new ArrayList<Attachment>();
        var documentCount = 0;
        for (var part : content) {
            var type = part.path("type").asString();
            if ("text".equals(type)) {
                var partText = part.get("text");
                if (partText == null || !partText.isString()) {
                    continue;
                }
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(partText.asString());
                continue;
            }
            if ("document".equals(type)) {
                documentCount++;
                if (documentCount > documentAttachmentGuard.maxCountPerMessage()) {
                    throw new BusinessException(
                            AssistantErrorCode.EXECUTION_DOCUMENT_ATTACHMENT_COUNT_LIMIT_EXCEEDED);
                }
                attachments.add(documentAttachment(part));
                continue;
            }
            if (!"image".equals(type)) {
                continue;
            }
            var source = requireObject(part.get("source"), "messages[].content[].source");
            if (!"url".equals(requireText(source, "type", "messages[].content[].source.type"))) {
                throw new IllegalArgumentException("图片附件仅支持已上传的 URL source");
            }
            requireText(source, "value", "messages[].content[].source.value");
            var metadata = requireObject(part.get("metadata"), "messages[].content[].metadata");
            var fileKey =
                    requireText(metadata, "filename", "messages[].content[].metadata.filename");
            attachments.add(new Attachment(AttachmentType.IMAGE, fileKey, null, fileKey));
        }
        return new UserInput(text.toString(), List.copyOf(attachments));
    }

    /**
     * 把 AG-UI {@code document} content part 转换为 {@code Attachment(TEXT, ...)}——react-ag-ui 的 {@code
     * toInputContent} 按 mimeType 把非图片/音频/视频的 {@code file} part 统一分流为 {@code document} 类型（见 {@code
     * conversions.js#mediaTypeForMime}）。文档在服务端解析完成后统一以既有 TEXT 附件语义进入执行链路， 不新增 {@code
     * AttachmentType.DOCUMENT} 枚举（AAF-114 #11410 设计边界）。TEXT 附件不允许设置 {@code resourceId}（与 {@code
     * AssistantExecutionService.parseAttachments} 现有校验一致），provenance 只记入日志， 不进入模型可见文本。
     */
    private Attachment documentAttachment(JsonNode part) {
        var metadata = requireObject(part.get("metadata"), "messages[].content[].metadata");
        var fileKey = requireText(metadata, "filename", "messages[].content[].metadata.filename");
        var guarded = documentAttachmentGuard.guardAndParse(fileKey);
        var split =
                documentTokenBudgetSplitter.split(
                        guarded.result(),
                        guarded.resourceId(),
                        guarded.contentHash(),
                        guarded.importerName());
        if (split.truncated()) {
            log.warn("文档附件内容超预算已截断: {}", split.provenance().toLogSummary());
        } else {
            log.debug("文档附件解析完成: {}", split.provenance().toLogSummary());
        }
        return new Attachment(AttachmentType.TEXT, guarded.result().title(), split.content(), null);
    }

    private static JsonNode requireObject(JsonNode value, String field) {
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException(field + " 必须是对象");
        }
        return value;
    }

    private static String requireText(JsonNode object, String field) {
        return requireText(object, field, field);
    }

    private static String requireText(JsonNode object, String field, String path) {
        var value = object.get(field);
        if (value == null || !value.isString() || value.asString().isBlank()) {
            throw new IllegalArgumentException(path + " 不能为空白");
        }
        return value.asString().trim();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return value.trim();
    }

    private static long requirePositiveLong(JsonNode object, String field, String path) {
        var value = object.get(field);
        if (value == null || !value.isIntegralNumber() || value.longValue() < 1) {
            throw new IllegalArgumentException(path + " 必须是正整数");
        }
        return value.longValue();
    }

    /**
     * 运行模式：唯一职责是把 {@code forwardedProps} 组装成统一的 {@link AssistantExecutionRequest}（外加可选 Team 目标）。
     *
     * <p>三种模式共用同一条执行链——{@link
     * AssistantExecutionService#start}，差异只在请求组装，因此把组装挂在枚举常量上而不是在入口写分支：新增模式必须在此实现组装，不会漏改调用点。
     */
    private enum Mode {
        /** 多轮对话：调用方只给 assistantId 与模型选择，其余执行选项取对话默认值。 */
        CHAT {
            @Override
            RunPlan plan(JsonNode props, UserInput input, Validator validator) {
                return new RunPlan(chatRequest(props, input), null);
            }
        },
        /** 单轮任务：调用方给出完整 `forwardedProps.request`，按 VO 契约校验。 */
        EXECUTION {
            @Override
            RunPlan plan(JsonNode props, UserInput input, Validator validator) {
                return new RunPlan(executionRequest(props, input.text(), validator), null);
            }
        },
        /** Team 协同：入参同 EXECUTION，额外指定已发布 Team 的冻结版本。 */
        TEAM {
            @Override
            RunPlan plan(JsonNode props, UserInput input, Validator validator) {
                return new RunPlan(
                        executionRequest(props, input.text(), validator),
                        new TeamTarget(
                                requireText(props, "teamId", "forwardedProps.teamId"),
                                requirePositiveLong(
                                        props, "teamVersion", "forwardedProps.teamVersion")));
            }
        };

        abstract RunPlan plan(JsonNode props, UserInput input, Validator validator);
    }

    /** 最后一条 user 消息解析结果：正文与已上传图片附件。 */
    private record UserInput(String text, List<Attachment> attachments) {}

    /** 组装结果：统一执行请求 + 可选 Team 目标（非 Team 模式为 null）。 */
    private record RunPlan(AssistantExecutionRequest request, TeamTarget team) {}

    /**
     * AG-UI 标准 run 入参。
     *
     * <p>字段形状对齐 AG-UI 协议 `RunAgentInput`（`@ag-ui/core` 的 `RunAgentInputSchema`），但不复用官方 Java 类：官方
     * {@code io.agentscope.core.agui.model.RunAgentInput} 的注解是 Jackson 2 的，在 Spring Boot 4 的
     * Jackson 3 {@code HttpMessageConverter} 下不生效，且该类落后于协议（缺 {@code parentRunId}）。详见 ADR-005 议题一。
     *
     * <p>两类入参的边界按协议语义划分，不可混用：
     *
     * <ul>
     *   <li>{@code forwardedProps} —— 本次 run 的一次性调用参数（`mode`、`request`、`assistantId`、
     *       `taskModelSelection`、`teamId`/`teamVersion`）。单向 client→server，不回写
     *   <li>{@code state} —— 线程级共享状态（页面感知上下文等）。双向语义，可被 `STATE_SNAPSHOT` 回吐； 服务端当前不消费，不得把调用参数放这里
     *   <li>{@code tools} / {@code context} —— 协议可选字段，标准客户端会发；AAF 不接受前端提供的工具与上下文，
     *       声明出来只为让"接收但不消费"成为显式契约，而不是靠全局忽略未知字段兜住
     *   <li>{@code resume} —— AG-UI 标准 interrupt 恢复入口（AAF-104 #10404）。非空时本次调用不新建 execution， 而是逐条按
     *       {@code interruptId}（对应 AAF {@code HumanApproval.approvalId}）解析审批决定并触发恢复， 见 {@link
     *       #run}。
     * </ul>
     */
    public record RunRequest(
            String threadId,
            String runId,
            String parentRunId,
            JsonNode forwardedProps,
            JsonNode state,
            List<RunMessage> messages,
            JsonNode tools,
            JsonNode context,
            List<ResumeEntry> resume) {
        public RunRequest {
            threadId = requireText(threadId, "threadId");
            runId = requireText(runId, "runId");
            forwardedProps = requireObject(forwardedProps, "forwardedProps");
            messages = List.copyOf(Objects.requireNonNull(messages, "messages 不能为空"));
            resume = resume == null ? List.of() : List.copyOf(resume);
        }
    }

    /**
     * AG-UI 标准 resume 条目：{@code interruptId} 对应待恢复 interrupt。{@code status} 表示 interrupt
     * 是否已解决或取消；审批场景另外由 {@code payload.approved} 表示批准或拒绝，澄清场景的 {@code payload} 直接承载补充字段。
     */
    public record ResumeEntry(String interruptId, String status, JsonNode payload) {
        public ResumeEntry {
            interruptId = requireText(interruptId, "resume[].interruptId");
            status = requireText(status, "resume[].status");
            if (!"resolved".equals(status) && !"cancelled".equals(status)) {
                throw new IllegalArgumentException("resume[].status 仅支持 resolved 或 cancelled");
            }
        }

        boolean resolved() {
            return "resolved".equals(status);
        }

        boolean approved() {
            if (!resolved()) {
                return false;
            }
            var approvedNode = payload == null ? null : payload.get("approved");
            return approvedNode != null && approvedNode.isBoolean() && approvedNode.asBoolean();
        }
    }

    public record RunMessage(String id, String role, JsonNode content) {
        public RunMessage {
            role = requireText(role, "message.role");
        }
    }
}
