package com.xuejiai.aaf.module.ui.canvas;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 画板 Yjs WebSocket 处理器。
 * 转发 Yjs 二进制消息给同一房间的其他客户端。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CanvasWebSocketHandler extends BinaryWebSocketHandler {

    private static final String ROOM_ATTR = "room";

    private final ObjectMapper objectMapper;

    /** 房间 → 连接集合 */
    private final ConcurrentHashMap<String, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        var room = extractRoom(session);
        if (room == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        session.getAttributes().put(ROOM_ATTR, room);
        rooms.computeIfAbsent(room, k -> new CopyOnWriteArraySet<>()).add(session);
        log.info("客户端加入画板房间: {}, 当前人数: {}", room, rooms.get(room).size());

        // 通知房间内其他人有新用户加入
        broadcastPresence(room);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        var room = (String) session.getAttributes().get(ROOM_ATTR);
        if (room == null) return;

        // 转发 Yjs 二进制消息给同房间其他客户端
        var members = rooms.get(room);
        if (members == null) return;

        for (var peer : members) {
            if (peer != session && peer.isOpen()) {
                try {
                    peer.sendMessage(message);
                } catch (IOException e) {
                    log.error("转发 Yjs 消息失败", e);
                }
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 心跳处理
        if ("ping".equals(message.getPayload())) {
            try {
                session.sendMessage(new TextMessage("pong"));
            } catch (Exception e) {
                log.error("发送 pong 失败", e);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        var room = (String) session.getAttributes().get(ROOM_ATTR);
        if (room == null) return;

        var members = rooms.get(room);
        if (members != null) {
            members.remove(session);
            if (members.isEmpty()) {
                rooms.remove(room);
            } else {
                broadcastPresence(room);
            }
        }
        log.info("客户端离开画板房间: {}", room);
    }

    /** 获取房间在线用户数 */
    public int getOnlineCount(String room) {
        var members = rooms.get(room);
        return members == null ? 0 : members.size();
    }

    /** 广播在线人数变更 */
    private void broadcastPresence(String room) {
        var members = rooms.get(room);
        if (members == null) return;

        try {
            var json = objectMapper.writeValueAsString(
                    java.util.Map.of("type", "presence", "count", members.size()));
            var msg = new TextMessage(json);
            for (var peer : members) {
                if (peer.isOpen()) {
                    peer.sendMessage(msg);
                }
            }
        } catch (IOException e) {
            log.error("广播在线状态失败", e);
        }
    }

    /** 从 URI 路径提取房间标识: /ws/canvas/{entitySlug}/{recordId} */
    private String extractRoom(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) return null;
        var path = uri.getPath();
        // 路径格式: /ws/canvas/{entitySlug}/{recordId}
        var parts = path.split("/");
        if (parts.length >= 5) {
            return parts[3] + "/" + parts[4];
        }
        return null;
    }
}
