package com.xuejiai.aaf.module.ai.chat.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.ErrorCode;
import com.xuejiai.aaf.framework.protection.RateLimit;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.chat.handler.AiChatHandler;
import com.xuejiai.aaf.module.ai.chat.handler.UserChatHandler;
import com.xuejiai.aaf.module.ai.chat.service.ChatService;
import com.xuejiai.aaf.module.ai.chat.vo.ChatRunRequest;
import com.xuejiai.aaf.module.ai.chat.vo.ChatSessionCreateDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 统一聊天运行端点（AI + 用户聊天）
 *
 * <p>Kiro Agent 走独立端点 {@code POST /api/autodev/kiro/context}（aaf-auto-dev 模块）。
 *
 * <h2>Spring AI 直连链路调用流程</h2>
 *
 * <pre>
 * 前端（assistant-ui / 自定义对话框）
 *   │  POST /api/chat/run  { threadId, modelId, messages, target:{type:"ai"} }
 *   ▼
 * ChatRunController.run()
 *   ├─ 解析 sessionId（持久化场景）
 *   ├─ 保存用户消息到 chat_message 表
 *   ├─ enrichWithHistory()：从 DB 追加最近 20 条历史消息
 *   └─ target.type="ai" → AiChatHandler.handle()
 *        │
 *        ├─ 虚拟线程异步执行（Thread.startVirtualThread）
 *        ├─ AgentRunContextHolder.open(runId, userId)
 *        ├─ 发布 RUN_STARTED 事件（agentRunEventPublisher）
 *        │
 *        ├─ ResilientChatService.stream(messages, modelId, userId)
 *        │    ├─ CapabilityRouter.resolve(ctx)：路由决策链
 *        │    │    按优先级：显式 modelId → 编排引擎 → AI 辅助 → 用户偏好 → 系统默认 → yaml 兜底
 *        │    ├─ AiCreditGuard.precheck()：积分/配额预检
 *        │    └─ DynamicChatClientFactory.get(modelId)
 *        │         ├─ 从 ai_model 表读取模型配置（apiKey、baseUrl、modelName）
 *        │         ├─ 构建 OpenAI 兼容 ChatClient（Caffeine 缓存 10 min）
 *        │         └─ 调用 LLM，返回 Flux&lt;ChatResponse&gt;
 *        │
 *        └─ AgUiStreamHandler.handleStream(flux, emitter, runId)
 *             ├─ SSE 推送 RUN_STARTED
 *             ├─ SSE 推送 TEXT_MESSAGE_START
 *             ├─ 每个 token → SSE 推送 TEXT_MESSAGE_CONTENT { delta: "..." }
 *             ├─ 流结束 → SSE 推送 TEXT_MESSAGE_END + RUN_FINISHED
 *             ├─ onComplete 回调：保存 AI 回复到 chat_message 表
 *             └─ 出错 → SSE 推送 RUN_ERROR
 *
 * 前端 ai-stream.ts 解析：
 *   data: {"type":"TEXT_MESSAGE_CONTENT","delta":"你好"}  → onChunk("你好")
 *   data: {"type":"RUN_ERROR","error":"..."}             → onError(...)
 *   其余事件类型忽略
 * </pre>
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "统一聊天")
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatRunController {

    private final AiChatHandler aiChatHandler;
    private final UserChatHandler userChatHandler;
    private final ChatService chatService;
    private final OperatorContext operatorContext;

    @Operation(summary = "统一聊天运行端点（ai / user，kiro 请走 /api/autodev/kiro/context）")
    @PostMapping("/run")
    @RateLimit(limit = 20, windowSeconds = 60, message = "对话请求过于频繁，请稍后再试")
    public SseEmitter run(@RequestBody @Valid ChatRunRequest request) {
        var userId = operatorContext.currentUserId().orElse(0L);

        // 解析 sessionId
        String sessionIdStr = request.state() != null ? request.state().sessionId() : null;
        if (sessionIdStr == null) sessionIdStr = request.threadId();

        Long sessionId = null;
        if (shouldPersist(request)) {
            try {
                sessionId = Long.valueOf(sessionIdStr);
            } catch (NumberFormatException ignored) {
            }
            if (sessionId == null) {
                var session =
                        chatService.createSession(
                                userId,
                                new ChatSessionCreateDTO(
                                        "新对话", request.target().type().toUpperCase()));
                sessionId = session.id();
            }
            String content =
                    request.messages().stream()
                            .filter(m -> "user".equals(m.role()))
                            .reduce((first, second) -> second)
                            .map(ChatRunRequest.AgUiMessage::content)
                            .orElse(null);
            if (content != null) {
                String awareness =
                        request.state() != null ? request.state().awarenessContext() : null;
                chatService.saveMessage(
                        userId, "HUMAN", sessionId, "user", content, "human", awareness);
            }
        }

        var enrichedRequest = enrichWithHistory(request, sessionId);
        final Long finalSessionId = sessionId;

        return switch (request.target().type()) {
            case "ai" -> aiChatHandler.handle(enrichedRequest, userId, finalSessionId);
            case "user" -> userChatHandler.handle(enrichedRequest, userId);
            default ->
                    throw new BusinessException(
                            ErrorCode.of(
                                    1_003_001,
                                    "不支持的 target.type: "
                                            + request.target().type()
                                            + "（kiro 请走 /api/autodev/kiro/context）"));
        };
    }

    private ChatRunRequest enrichWithHistory(ChatRunRequest request, Long sessionId) {
        if (sessionId == null) return request;
        try {
            var history =
                    chatService.listMessages(sessionId).stream()
                            .limit(20)
                            .map(m -> new ChatRunRequest.AgUiMessage(m.role(), m.content()))
                            .toList();
            var merged = new java.util.ArrayList<>(history);
            merged.addAll(request.messages());
            return new ChatRunRequest(
                    request.threadId(),
                    merged,
                    request.target(),
                    request.state(),
                    request.modelId());
        } catch (Exception e) {
            log.warn("加载历史消息失败，使用前端传来的消息: sessionId={}", sessionId, e);
            return request;
        }
    }

    private boolean shouldPersist(ChatRunRequest request) {
        if (request.state() != null && request.state().persist() != null) {
            return request.state().persist();
        }
        return true; // ai/user 默认持久化
    }
}
