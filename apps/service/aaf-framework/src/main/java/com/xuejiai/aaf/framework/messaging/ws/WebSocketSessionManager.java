package com.xuejiai.aaf.framework.messaging.ws;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import lombok.extern.slf4j.Slf4j;

/**
 * 管理在线用户 WebSocket 会话，提供按用户推送能力。
 *
 * <p>会话按 {@code (userId, channel)} 复合键存储，支持同一用户同时打开多个端点连接
 * （如 Notification + IM）而不互相覆盖。{@link #sendToUser} 广播给该用户所有已连接端点。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Component
public class WebSocketSessionManager {

    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, WebSocketSession>> sessions =
            new ConcurrentHashMap<>();

    /**
     * 注册用户会话
     *
     * @param userId 用户 ID
     * @param channel 连接端点标识（如 "notifications" / "im"），用于区分同一用户的多端点连接
     * @param session WebSocket 会话
     */
    public void register(Long userId, String channel, WebSocketSession session) {
        sessions.computeIfAbsent(userId, k -> new ConcurrentHashMap<>()).put(channel, session);
        log.info(
                "用户 {} WebSocket 已连接: channel={}，当前在线用户数 {}",
                userId,
                channel,
                sessions.size());
    }

    /**
     * 移除用户会话
     *
     * @param userId 用户 ID
     * @param channel 连接端点标识
     */
    public void remove(Long userId, String channel) {
        var channels = sessions.get(userId);
        if (channels == null) {
            return;
        }
        channels.remove(channel);
        if (channels.isEmpty()) {
            sessions.remove(userId);
        }
        log.info("用户 {} WebSocket 已断开: channel={}，当前在线用户数 {}", userId, channel, sessions.size());
    }

    /**
     * 向指定用户推送消息，广播给该用户所有已连接端点（Notification/IM 等）
     *
     * @param userId 用户 ID
     * @param message 消息 JSON 字符串
     */
    public void sendToUser(Long userId, String message) {
        var channels = sessions.get(userId);
        if (channels == null) {
            return;
        }
        var textMessage = new TextMessage(message);
        channels.forEach(
                (channel, session) -> {
                    if (session.isOpen()) {
                        try {
                            session.sendMessage(textMessage);
                        } catch (IOException e) {
                            log.error("推送消息给用户 {} 失败: channel={}", userId, channel, e);
                        }
                    }
                });
    }
}
