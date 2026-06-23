package com.xuejiai.aaf.module.ai.chat.service;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.ai.chat.ResilientChatService;
import com.xuejiai.aaf.framework.intelligent.assistant.AssistantService;
import com.xuejiai.aaf.framework.security.access.AccessContext;
import com.xuejiai.aaf.framework.security.access.AccessLayer;
import com.xuejiai.aaf.framework.security.access.ServicePermissionChecker;
import com.xuejiai.aaf.module.ai.chat.agui.AgUiEvent;
import com.xuejiai.aaf.module.ai.chat.vo.ChatRunRequest;
import com.xuejiai.aaf.module.ai.chat.vo.ChatSessionCreateDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 统一对话编排服务——所有对话接口的唯一入口
 *
 * <p>职责：
 *
 * <ul>
 *   <li>会话管理（创建/加载历史）
 *   <li>消息持久化
 *   <li>委托给 AssistantService 执行完整智能链路
 *   <li>统一 SSE 流输出
 * </ul>
 *
 * <p>所有接口（REST/WebSocket/AG-UI/A2A/MCP）统一调用本服务， 不再直接调用 ResilientChatService。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatOrchestrationService {

    private final ChatService chatService;
    private final AssistantService assistantService;
    private final ServicePermissionChecker permissionChecker;
    private final ResilientChatService chatLlm;

    private static final long SSE_TIMEOUT = 5 * 60 * 1000L;

    /**
     * 统一对话入口。
     *
     * @param request 对话请求
     * @param userId 已认证的用户 ID（由 Layer 1/2 保证）
     * @return SSE 流
     */
    public SseEmitter execute(ChatRunRequest request, Long userId) {
        // Layer 3 权限：检查用户是否有权访问目标 session
        var sessionIdStr = request.state() != null ? request.state().sessionId() : null;
        if (sessionIdStr != null) {
            permissionChecker.require(userId, "session", sessionIdStr, "write");
        }
        AccessContext.markProcessed(AccessLayer.SERVICE);

        // 会话管理
        Long sessionId = resolveSession(request, userId);

        // 持久化用户消息
        var userContent = extractLastUserMessage(request.messages());
        if (userContent != null && sessionId != null) {
            var awareness = request.state() != null ? request.state().awarenessContext() : null;
            chatService.saveMessage(
                    userId, "HUMAN", sessionId, "user", userContent, "human", awareness);
        }

        // 构建 SSE 流
        var emitter = new SseEmitter(SSE_TIMEOUT);
        var runId = java.util.UUID.randomUUID().toString();

        // 异步执行智能链路
        Thread.startVirtualThread(
                () -> {
                    try {
                        sendEvent(emitter, AgUiEvent.runStarted(runId));
                        var messageId = java.util.UUID.randomUUID().toString();
                        sendEvent(emitter, AgUiEvent.textMessageStart(runId, messageId));

                        // 委托给 AssistantService 完整链路
                        var assistantId = resolveAssistantId(request);
                        var response =
                                assistantService.handle(
                                        sessionId != null ? sessionId.toString() : runId,
                                        userId,
                                        assistantId,
                                        userContent != null ? userContent : "");

                        sendEvent(
                                emitter,
                                AgUiEvent.textMessageContent(runId, messageId, response.content()));
                        sendEvent(emitter, AgUiEvent.textMessageEnd(runId, messageId));

                        // 生成追问建议
                        try {
                            var suggestions =
                                    generateSuggestions(userContent, response.content(), userId);
                            if (!suggestions.isEmpty()) {
                                sendEvent(emitter, AgUiEvent.custom("suggestions", suggestions));
                            }
                        } catch (Exception e) {
                            log.debug("建议生成失败，跳过: {}", e.getMessage());
                        }

                        sendEvent(emitter, AgUiEvent.runFinished(runId));
                        emitter.complete();

                        // 持久化助理响应
                        if (sessionId != null) {
                            chatService.saveMessage(
                                    userId,
                                    "AI",
                                    sessionId,
                                    "assistant",
                                    response.content(),
                                    "ai",
                                    null);
                        }
                    } catch (Exception e) {
                        log.error("对话执行失败: {}", e.getMessage(), e);
                        try {
                            sendEvent(emitter, AgUiEvent.runError(runId, e.getMessage()));
                            emitter.complete();
                        } catch (Exception ignored) {
                        }
                    }
                });

        return emitter;
    }

    /**
     * WebSocket 消息处理入口（同步返回文本）
     *
     * @param sessionId 会话 ID
     * @param userId 用户 ID
     * @param userInput 用户输入文本
     * @return AI 回复文本
     */
    public String executeSync(String sessionId, Long userId, String userInput) {
        permissionChecker.require(userId, "session", sessionId, "write");
        var response = assistantService.handle(sessionId, userId, "default", userInput);
        return response.content();
    }

    private Long resolveSession(ChatRunRequest request, Long userId) {
        var sessionIdStr = request.state() != null ? request.state().sessionId() : null;
        if (sessionIdStr == null) sessionIdStr = request.threadId();

        try {
            return Long.valueOf(sessionIdStr);
        } catch (NumberFormatException e) {
            // 创建新会话
            var session =
                    chatService.createSession(
                            userId,
                            new ChatSessionCreateDTO("新对话", request.target().type().toUpperCase()));
            return session.id();
        }
    }

    private String extractLastUserMessage(List<ChatRunRequest.AgUiMessage> messages) {
        if (messages == null || messages.isEmpty()) return null;
        return messages.stream()
                .filter(m -> "user".equals(m.role()))
                .reduce((first, second) -> second)
                .map(ChatRunRequest.AgUiMessage::content)
                .orElse(null);
    }

    private String resolveAssistantId(ChatRunRequest request) {
        if (request.target().agentRole() != null) return request.target().agentRole();
        return "default";
    }

    private void sendEvent(SseEmitter emitter, AgUiEvent event) {
        try {
            emitter.send(SseEmitter.event().name("message").data(event));
        } catch (Exception e) {
            log.debug("SSE 发送失败: {}", e.getMessage());
        }
    }

    /** 根据对话内容生成追问建议，失败时返回空列表 */
    private List<Map<String, String>> generateSuggestions(
            String userInput, String aiReply, Long userId) throws Exception {
        List<org.springframework.ai.chat.messages.Message> messages =
                List.of(
                        new SystemMessage(
                                """
                        根据以下对话，生成3条用户可能想追问的问题。
                        要求：简短精炼（10字以内），与对话内容强相关，用中文。
                        只返回 JSON 数组，格式：[{"prompt":"问题1"},{"prompt":"问题2"},{"prompt":"问题3"}]
                        不要任何多余内容。
                        """),
                        new UserMessage("用户问：%s\nAI回答：%s".formatted(userInput, aiReply)));
        var response = chatLlm.call(messages, null, userId);
        var text = response.getResult().getOutput().getText().trim();
        var start = text.indexOf('[');
        var end = text.lastIndexOf(']');
        if (start < 0 || end < 0) return List.of();
        return JsonUtils.parseObject(
                text.substring(start, end + 1), new tools.jackson.core.type.TypeReference<>() {});
    }

    // ── 静态默认建议（按 agentId 分类，AI 生成失败时兜底） ──────────────────────
    private static final Map<String, List<Map<String, String>>> DEFAULT_SUGGESTIONS =
            Map.of(
                    "default",
                            List.of(
                                    Map.of("prompt", "帮我生成一段营销文案"),
                                    Map.of("prompt", "用 AI 写一篇博客文章"),
                                    Map.of("prompt", "如何用 AI 提升工作效率？")),
                    "kiro",
                            List.of(
                                    Map.of("prompt", "帮我生成一个 REST 接口"),
                                    Map.of("prompt", "用 AI 分析并重构这段代码"),
                                    Map.of("prompt", "自动生成单元测试")));

    /** 欢迎页建议：直接返回静态默认值，不调 LLM（避免每次刷新页面消耗积分） */
    public List<Map<String, String>> getWelcomeSuggestions(String agentId, Long userId) {
        return DEFAULT_SUGGESTIONS.getOrDefault(agentId, DEFAULT_SUGGESTIONS.get("default"));
    }
}
