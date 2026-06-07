package com.xuejiai.aaf.framework.messaging.internal;

/**
 * 站内信发送器接口，由业务层实现（依赖 Notification 实体和 WebSocket）。
 *
 * <p>通过此接口发送的消息同时写入持久化存储并通过 WebSocket 实时推送（用户在线时）。
 */
public interface InternalMessageSender {

    /** 发送站内信给指定用户（完整参数） */
    void send(
            Long userId,
            String type,
            String title,
            String body,
            String relatedUrl,
            String entityType,
            Long entityId);

    /** 发送站内信给指定用户（简化版，无跳转和关联实体） */
    default void send(Long userId, String title, String body) {
        send(userId, "system", title, body, null, null, null);
    }
}
