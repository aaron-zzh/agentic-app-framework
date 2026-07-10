package com.xuejiai.aaf.module.ai.chat.handler;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.messaging.ws.ImMessage;
import com.xuejiai.aaf.framework.messaging.ws.WebSocketMessageSender;
import com.xuejiai.aaf.module.ai.chat.agui.AgUiEvent;
import com.xuejiai.aaf.module.ai.chat.service.ChatService;
import com.xuejiai.aaf.module.ai.chat.vo.ChatRunRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户间聊天处理器
 *
 * <p>消息已由 {@code ChatRunController} 保存（{@code shouldPersist} 分支），本处理器只负责
 * 通过 {@link WebSocketMessageSender} 推送给目标用户，不重复保存。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserChatHandler {

    private static final long SSE_TIMEOUT = 30 * 1000L;

    private final ChatService chatService;
    private final WebSocketMessageSender messageSender;

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
                        Long targetUserId = request.target().userId();
                        String sessionIdStr =
                                request.state() != null ? request.state().sessionId() : null;
                        if (targetUserId != null && sessionIdStr != null) {
                            var sessionId = Long.valueOf(sessionIdStr);
                            // 消息已由 ChatRunController 保存，此处只取最新一条推送
                            chatService.listMessages(sessionId).stream()
                                    .reduce((first, second) -> second)
                                    .ifPresent(
                                            latest ->
                                                    messageSender.send(
                                                            targetUserId,
                                                            new ImMessage(
                                                                    sessionId,
                                                                    latest.id(),
                                                                    String.valueOf(
                                                                            latest.senderId()),
                                                                    latest.content())));
                        }

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
            var json = JsonUtils.toJsonString(event);
            emitter.send(SseEmitter.event().data(json));
        } catch (Exception e) {
            log.debug("SSE 发送失败: {}", e.getMessage());
        }
    }
}
