package com.xuejiai.aaf.module.ai.chat.ws;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 聊天 WebSocket 处理器——服务端推送通道。
 *
 * <p>消息发送走 REST 接口（{@code POST /api/system/chat/messages}）， WebSocket
 * 仅用于服务端向客户端推送新消息，不处理客户端发送的消息内容。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final String USER_ID_ATTR = "userId";
    private static final String SESSION_ID_ATTR = "sessionId";

    /** 按聊天会话 ID 管理 WebSocket 连接 */
    private final ConcurrentHashMap<Long, ConcurrentHashMap<Long, WebSocketSession>>
            sessionConnections = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        var userId = extractParam(session, "userId");
        var sessionId = extractParam(session, "sessionId");
        if (userId == null || sessionId == null) {
            log.warn("聊天 WebSocket 连接缺少参数，关闭连接");
            try {
                session.close(CloseStatus.BAD_DATA);
            } catch (Exception ignored) {
            }
            return;
        }
        session.getAttributes().put(USER_ID_ATTR, userId);
        session.getAttributes().put(SESSION_ID_ATTR, sessionId);
        sessionConnections
                .computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
                .put(userId, session);
        log.info("用户 {} 加入聊天会话 {}", userId, sessionId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message)
            throws Exception {
        // WebSocket 只用于服务端推送，客户端发送消息走 REST 接口
        // 仅处理心跳
        if ("ping".equals(message.getPayload())) {
            session.sendMessage(new TextMessage("pong"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        var userId = (Long) session.getAttributes().get(USER_ID_ATTR);
        var sessionId = (Long) session.getAttributes().get(SESSION_ID_ATTR);
        if (userId != null && sessionId != null) {
            var connections = sessionConnections.get(sessionId);
            if (connections != null) {
                connections.remove(userId);
                if (connections.isEmpty()) {
                    sessionConnections.remove(sessionId);
                }
            }
            log.info("用户 {} 离开聊天会话 {}", userId, sessionId);
        }
    }

    /**
     * 向指定会话广播消息
     *
     * @param sessionId 会话 ID
     * @param json 消息 JSON 字符串
     */
    public void broadcast(Long sessionId, String json) {
        var connections = sessionConnections.get(sessionId);
        if (connections == null) {
            return;
        }
        var textMessage = new TextMessage(json);
        connections
                .values()
                .forEach(
                        ws -> {
                            if (ws.isOpen()) {
                                try {
                                    ws.sendMessage(textMessage);
                                } catch (IOException e) {
                                    log.error("广播消息失败", e);
                                }
                            }
                        });
    }

    private Long extractParam(WebSocketSession session, String param) {
        URI uri = session.getUri();
        if (uri == null) {
            return null;
        }
        var params = UriComponentsBuilder.fromUri(uri).build().getQueryParams();
        var value = params.getFirst(param);
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
