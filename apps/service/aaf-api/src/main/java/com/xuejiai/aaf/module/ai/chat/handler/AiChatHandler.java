package com.xuejiai.aaf.module.ai.chat.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.framework.intelligent.agent.run.AgentRunContextHolder;
import com.xuejiai.aaf.framework.intelligent.agent.run.AgentRunEventPublisher;
import com.xuejiai.aaf.framework.intelligent.agent.run.AgentRunEventType;
import com.xuejiai.aaf.framework.intelligent.ai.chat.AiProperties;
import com.xuejiai.aaf.framework.intelligent.ai.chat.ResilientChatService;
import com.xuejiai.aaf.module.ai.chat.agui.AgUiEvent;
import com.xuejiai.aaf.module.ai.chat.agui.AgUiStreamHandler;
import com.xuejiai.aaf.module.ai.chat.agui.AgentRunEventStreamService;
import com.xuejiai.aaf.module.ai.chat.service.ChatService;
import com.xuejiai.aaf.module.ai.chat.vo.ChatRunRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 对话处理器
 *
 * <p>通过 ResilientChatService 流式调用 LLM，流结束后持久化 AI 回复。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiChatHandler {

    private static final long SSE_TIMEOUT = 5 * 60 * 1000L;

    private final ResilientChatService resilientChatService;
    private final AgUiStreamHandler streamHandler;
    private final AiProperties aiProperties;
    private final ChatService chatService;
    private final AgentRunEventStreamService agentRunEventStreamService;
    private final AgentRunEventPublisher agentRunEventPublisher;

    /**
     * 处理 AI 对话请求
     *
     * @param request 请求体（messages 已由 ChatRunController 合并历史）
     * @param userId 当前用户 ID
     * @param sessionId 会话 ID（非 null 时流结束后持久化 AI 回复）
     * @return SSE 流
     */
    public SseEmitter handle(ChatRunRequest request, Long userId, Long sessionId) {
        var emitter = new SseEmitter(SSE_TIMEOUT);
        var runId = UUID.randomUUID().toString();
        agentRunEventStreamService.attach(
                runId, emitter, AgentRunEventStreamService.Format.AGUI_CUSTOM);

        emitter.onCompletion(() -> log.debug("AI SSE 完成: runId={}", runId));
        emitter.onTimeout(() -> log.warn("AI SSE 超时: runId={}", runId));

        var messages = buildMessages(request);

        Thread.startVirtualThread(
                () -> {
                    try (var ignored = AgentRunContextHolder.open(runId, userId, null)) {
                        agentRunEventPublisher.publish(
                                AgentRunEventType.RUN_STARTED,
                                "运行开始",
                                "AI 对话运行已启动",
                                java.util.Map.of("sessionId", sessionId != null ? sessionId : 0L));
                        var flux = resilientChatService.stream(messages, "chat", userId);
                        // 流结束后持久化 AI 回复
                        streamHandler.handleStream(
                                flux,
                                emitter,
                                runId,
                                fullContent -> {
                                    if (sessionId != null
                                            && fullContent != null
                                            && !fullContent.isBlank()) {
                                        try {
                                            chatService.saveMessage(
                                                    0L, "AI", sessionId, "assistant", fullContent);
                                        } catch (Exception e) {
                                            log.warn("持久化 AI 回复失败: sessionId={}", sessionId, e);
                                        }
                                    }
                                });
                    } catch (Exception e) {
                        agentRunEventPublisher.publish(
                                AgentRunEventType.RUN_ERROR,
                                "运行失败",
                                e.getMessage(),
                                java.util.Map.of("sessionId", sessionId != null ? sessionId : 0L));
                        log.error("AI 流式调用失败: runId={}", runId, e);
                        sendErrorAndComplete(emitter, runId, e);
                    }
                });

        return emitter;
    }

    private List<Message> buildMessages(ChatRunRequest request) {
        var systemPrompt = aiProperties.getPrompts().getOrDefault("chat", "你是一个有帮助的 AI 助手。");
        var messages = new ArrayList<Message>();
        messages.add(new SystemMessage(systemPrompt));
        for (var msg : request.messages()) {
            messages.add(
                    switch (msg.role()) {
                        case "assistant" -> new AssistantMessage(msg.content());
                        case "system" -> new SystemMessage(msg.content());
                        default -> new UserMessage(msg.content());
                    });
        }
        return messages;
    }

    private void sendErrorAndComplete(SseEmitter emitter, String runId, Exception e) {
        try {
            var json =
                    new com.fasterxml.jackson.databind.ObjectMapper()
                            .writeValueAsString(AgUiEvent.runError(runId, e.getMessage()));
            emitter.send(SseEmitter.event().data(json));
        } catch (Exception ignored) {
        }
        try {
            emitter.completeWithError(e);
        } catch (Exception ignored) {
        }
    }
}
