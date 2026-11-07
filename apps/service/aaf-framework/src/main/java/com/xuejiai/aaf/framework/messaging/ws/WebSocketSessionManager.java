package com.xuejiai.aaf.framework.messaging.ws;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.SessionLimitExceededException;

import lombok.extern.slf4j.Slf4j;

/**
 * 管理在线用户 WebSocket 会话，提供按用户推送能力。
 *
 * <p>会话按 {@code userId → sessionId} 存储，同一用户的多个端点（如 Notification + IM）与多个标签页连接互不覆盖；
 * 断开时只移除该连接本身。{@link #sendToUser} 广播给该用户全部已连接会话。
 *
 * <p>原始 {@link WebSocketSession} 不支持并发发送，注册时统一包装为 {@link ConcurrentWebSocketSessionDecorator}。
 * 业务推送与心跳回复都必须经本类发送，不得直接写原始会话。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Component
public class WebSocketSessionManager {

    /** 单条消息发送超过该时长视为慢连接，由装饰器关闭会话 */
    private static final int SEND_TIME_LIMIT_MS = 10_000;

    /** 慢连接待发送缓冲上限，超过后由装饰器关闭会话 */
    private static final int BUFFER_SIZE_LIMIT_BYTES = 512 * 1024;

    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, ChannelSession>> sessions =
            new ConcurrentHashMap<>();

    /**
     * 注册用户会话
     *
     * @param userId 用户 ID
     * @param channel 连接端点标识（如 "notifications" / "im"），用于日志定位
     * @param session WebSocket 会话
     */
    public void register(Long userId, String channel, WebSocketSession session) {
        var concurrent =
                new ConcurrentWebSocketSessionDecorator(
                        session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT_BYTES);
        sessions.compute(
                userId,
                (key, userSessions) -> {
                    var target =
                            userSessions == null
                                    ? new ConcurrentHashMap<String, ChannelSession>()
                                    : userSessions;
                    target.put(session.getId(), new ChannelSession(channel, concurrent));
                    return target;
                });
        log.info("用户 {} WebSocket 已连接: channel={}，当前在线用户数 {}", userId, channel, sessions.size());
    }

    /**
     * 移除用户会话，只移除该连接，不影响同一用户的其他连接
     *
     * @param userId 用户 ID
     * @param session 已断开的 WebSocket 会话
     */
    public void remove(Long userId, WebSocketSession session) {
        sessions.computeIfPresent(
                userId,
                (key, userSessions) -> {
                    userSessions.remove(session.getId());
                    return userSessions.isEmpty() ? null : userSessions;
                });
        log.info(
                "用户 {} WebSocket 已断开: sessionId={}，当前在线用户数 {}",
                userId,
                session.getId(),
                sessions.size());
    }

    /**
     * 向指定用户推送消息，广播给该用户所有已连接会话（Notification/IM 等）
     *
     * @param userId 用户 ID
     * @param message 消息 JSON 字符串
     */
    public void sendToUser(Long userId, String message) {
        var userSessions = sessions.get(userId);
        if (userSessions == null) {
            return;
        }
        var textMessage = new TextMessage(message);
        userSessions.forEach((sessionId, registered) -> send(userId, registered, textMessage));
    }

    /**
     * 向用户的某个已注册会话发送消息（如心跳回复），与业务推送共用同一并发装饰器；会话未注册或已移除时不发送
     *
     * @param userId 用户 ID
     * @param session 收到消息的原始会话
     * @param message 文本内容
     */
    public void sendToSession(Long userId, WebSocketSession session, String message) {
        if (userId == null) {
            return;
        }
        var userSessions = sessions.get(userId);
        var registered = userSessions == null ? null : userSessions.get(session.getId());
        if (registered == null) {
            return;
        }
        send(userId, registered, new TextMessage(message));
    }

    private void send(Long userId, ChannelSession registered, TextMessage message) {
        var session = registered.session();
        if (!session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(message);
        } catch (SessionLimitExceededException e) {
            // 装饰器已关闭慢连接，afterConnectionClosed 会移除会话
            log.warn(
                    "用户 {} WebSocket 发送超限，连接已关闭: channel={}, sessionId={}, reason={}",
                    userId,
                    registered.channel(),
                    session.getId(),
                    e.getMessage());
        } catch (IOException e) {
            log.error(
                    "推送消息给用户 {} 失败: channel={}, sessionId={}",
                    userId,
                    registered.channel(),
                    session.getId(),
                    e);
        }
    }

    private record ChannelSession(String channel, WebSocketSession session) {}
}
