package com.xuejiai.aaf.module.chat.message.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.xuejiai.aaf.framework.messaging.ws.WebSocketSessionManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * IM WebSocket 处理器，处理用户间聊天连接的建立、断开与心跳。
 *
 * <p>连接身份为 userId（一人一连接），实际消息推送由
 * {@code MessageCrudService.pushToParticipants} 通过 {@link WebSocketSessionManager}
 * 完成，本 handler 只负责连接生命周期管理。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImWebSocketHandler extends TextWebSocketHandler {

    private static final String PING = "ping";
    private static final String PONG = "pong";
    private static final String USER_ID_ATTR = "userId";
    private static final String CHANNEL = "im";

    private final WebSocketSessionManager sessionManager;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // userId 由 JwtHandshakeInterceptor 从 JWT 解析后写入 attributes
        var userId = (Long) session.getAttributes().get(USER_ID_ATTR);
        if (userId == null) {
            log.warn("IM WebSocket 连接缺少 userId，关闭连接（JWT 认证未通过？）");
            try {
                session.close(CloseStatus.BAD_DATA);
            } catch (Exception ignored) {
            }
            return;
        }
        sessionManager.register(userId, CHANNEL, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message)
            throws Exception {
        // 心跳：客户端发 ping，服务端回 pong。业务消息发送走 REST 接口，不经此通道。
        if (PING.equals(message.getPayload())) {
            session.sendMessage(new TextMessage(PONG));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        var userId = (Long) session.getAttributes().get(USER_ID_ATTR);
        if (userId != null) {
            sessionManager.remove(userId, CHANNEL);
        }
    }
}
