package com.xuejiai.aaf.module.system.notify.ws;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import lombok.extern.slf4j.Slf4j;

/**
 * 管理在线用户 WebSocket 会话，提供按用户推送能力。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Component
public class WebSocketSessionManager {

    private final ConcurrentHashMap<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /** 注册用户会话 */
    public void register(Long userId, WebSocketSession session) {
        sessions.put(userId, session);
        log.info("用户 {} WebSocket 已连接，当前在线 {}", userId, sessions.size());
    }

    /** 移除用户会话 */
    public void remove(Long userId) {
        sessions.remove(userId);
        log.info("用户 {} WebSocket 已断开，当前在线 {}", userId, sessions.size());
    }

    /** 向指定用户推送消息 */
    public void sendToUser(Long userId, String message) {
        var session = sessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                log.error("推送消息给用户 {} 失败", userId, e);
            }
        }
    }
}
