package com.xuejiai.aaf.module.ai.chat.handler;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.xuejiai.aaf.module.ai.chat.agui.AgUiEvent;
import com.xuejiai.aaf.module.ai.chat.service.ChatService;
import com.xuejiai.aaf.module.ai.chat.vo.ChatRunRequest;
import com.xuejiai.aaf.module.ai.chat.ws.ChatWebSocketHandler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户间聊天处理器
 *
 * <p>保存消息并通过 WebSocket 推送给目标用户。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserChatHandler {

    private static final long SSE_TIMEOUT = 30 * 1000L;

    private final ChatService chatService;
    private final ChatWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper;

    /**
     * 处理用户间聊天请求
     *
     * @param request 聊天运行请求
     * @param senderId 发送者用户 ID
     * @return SSE 流
     */
    public SseEmitter handle(ChatRunRequest request, Long senderId) {
        var emitter = new SseEmitter(SSE_TIMEOUT);
        var runId = UUID.randomUUID().toString();

        Thread.startVirtualThread(
                () -> {
                    try {
                        // 提取最后一条 user 消息内容
                        String content =
                                request.messages().stream()
                                        .filter(m -> "user".equals(m.role()))
                                        .reduce((first, second) -> second)
                                        .map(ChatRunRequest.AgUiMessage::content)
                                        .orElse("");

                        // 通过 WebSocket 推送给目标用户所在的会话
                        Long targetUserId = request.target().userId();
                        String sessionId =
                                request.state() != null ? request.state().sessionId() : null;
                        if (sessionId != null) {
                            var saved =
                                    chatService.saveMessage(
                                            senderId,
                                            "HUMAN",
                                            Long.valueOf(sessionId),
                                            "user",
                                            content);
                            var json = objectMapper.writeValueAsString(saved);
                            webSocketHandler.broadcast(Long.valueOf(sessionId), json);
                        }

                        // SSE 返回 RUN_FINISHED 确认
                        sendEvent(emitter, AgUiEvent.runStarted(runId));
                        sendEvent(emitter, AgUiEvent.runFinished(runId));
                        emitter.complete();
                    } catch (Exception e) {
                        log.error("用户聊天处理失败: runId={}", runId, e);
                        try {
                            sendEvent(emitter, AgUiEvent.runError(runId, e.getMessage()));
                            emitter.completeWithError(e);
                        } catch (Exception ignored) {
                        }
                    }
                });

        return emitter;
    }

    private void sendEvent(SseEmitter emitter, AgUiEvent event) {
        try {
            var json = objectMapper.writeValueAsString(event);
            emitter.send(SseEmitter.event().data(json));
        } catch (Exception e) {
            log.debug("SSE 发送失败: {}", e.getMessage());
        }
    }
}
