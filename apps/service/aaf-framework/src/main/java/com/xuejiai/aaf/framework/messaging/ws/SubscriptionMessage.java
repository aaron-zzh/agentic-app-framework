package com.xuejiai.aaf.framework.messaging.ws;

/**
 * 字段变更订阅通知消息。
 *
 * @param entityType 变更的实体类型
 * @param entityId 变更的实体 ID
 * @author AaronZZH & Kiro
 */
public record SubscriptionMessage(String entityType, Long entityId) implements WsMessage {

    @Override
    public String type() {
        return "subscription";
    }
}
