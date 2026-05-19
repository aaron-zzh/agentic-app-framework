package com.xuejiai.aaf.module.system.ws;

import java.net.URI;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 通知 WebSocket 处理器，处理连接/断开/心跳。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private static final String PING = "ping";
    private static final String PONG = "pong";
    private static final String USER_ID_ATTR = "userId";

    private final WebSocketSessionManager sessionManager;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        var userId = extractUserId(session);
        if (userId == null) {
            log.warn("WebSocket 连接缺少 userId 参数，关闭连接");
            try {
                session.close(CloseStatus.BAD_DATA);
            } catch (Exception ignored) {
            }
            return;
        }
        session.getAttributes().put(USER_ID_ATTR, userId);
        sessionManager.register(userId, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 心跳：客户端发 ping，服务端回 pong
        if (PING.equals(message.getPayload())) {
            session.sendMessage(new TextMessage(PONG));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        var userId = (Long) session.getAttributes().get(USER_ID_ATTR);
        if (userId != null) {
            sessionManager.remove(userId);
        }
    }

    /** 从 URL query param 中提取 userId */
    private Long extractUserId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            return null;
        }
        var params = UriComponentsBuilder.fromUri(uri).build().getQueryParams();
        var value = params.getFirst("userId");
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
