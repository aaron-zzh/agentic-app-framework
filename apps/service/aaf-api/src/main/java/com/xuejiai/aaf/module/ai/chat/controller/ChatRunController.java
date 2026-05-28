package com.xuejiai.aaf.module.ai.chat.controller;

import com.xuejiai.aaf.module.ai.chat.vo.ChatSessionCreateDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.ErrorCode;
import com.xuejiai.aaf.framework.security.ActorContext;
import com.xuejiai.aaf.module.ai.chat.handler.AiChatHandler;
import com.xuejiai.aaf.module.ai.chat.handler.UserChatHandler;
import com.xuejiai.aaf.module.ai.chat.service.ChatService;
import com.xuejiai.aaf.module.ai.chat.vo.ChatRunRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 统一聊天运行端点（AI + 用户聊天）
 *
 * <p>Kiro Agent 走独立端点 {@code POST /api/autodev/kiro/run}（aaf-auto-dev 模块）。
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
    private final ActorContext actorContext;

    @Operation(summary = "统一聊天运行端点（ai / user，kiro 请走 /api/autodev/kiro/run）")
    @PostMapping("/run")
    public SseEmitter run(@RequestBody @Valid ChatRunRequest request) {
        var userId = actorContext.currentUserId().orElse(0L);

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
                                            + "（kiro 请走 /api/autodev/kiro/run）"));
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
                    request.threadId(), merged, request.target(), request.state());
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
