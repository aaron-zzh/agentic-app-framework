package com.xuejiai.aaf.framework.messaging.ws;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.util.JsonUtils;

import lombok.RequiredArgsConstructor;

/**
 * WebSocket 消息推送统一入口。
 *
 * <p>业务方通过本类推送强类型 {@link WsMessage}，不直接操作
 * {@link WebSocketSessionManager} 或手写 JSON 序列化，保证消息信封格式统一
 * （{@code {type, data}}）且字段类型安全。
 *
 * @author AaronZZH & Kiro
 */
@Component
@RequiredArgsConstructor
public class WebSocketMessageSender {

    private final WebSocketSessionManager sessionManager;

    /**
     * 推送消息给指定用户，广播给该用户所有已连接端点。
     *
     * @param userId 目标用户 ID
     * @param message 强类型消息体
     */
    public void send(Long userId, WsMessage message) {
        sessionManager.sendToUser(userId, serialize(message));
    }

    /** 序列化为 {@code {type, data}} 信封 JSON，供需要自行选择推送渠道的场景使用（如同时走 SSE） */
    public String serialize(WsMessage message) {
        return JsonUtils.toJsonString(Map.of("type", message.type(), "data", message));
    }
}
