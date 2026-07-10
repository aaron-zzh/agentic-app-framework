package com.xuejiai.aaf.framework.messaging.ws;

/**
 * WebSocket 推送消息强类型基接口。
 *
 * <p>所有业务消息类型必须实现本接口并声明唯一 {@link #type()}，替代裸 {@code Map<String, Object>}
 * 拼装，前端可按 {@code type} 字段结合各消息记录类字段做类型安全解析。
 *
 * <p>新增消息类型时，在 {@code permits} 列表中登记对应记录类。
 *
 * @author AaronZZH & Kiro
 */
public sealed interface WsMessage
        permits NotificationMessage, SubscriptionMessage, ImMessage {

    /** 消息类型标识，用于前端区分处理逻辑 */
    String type();
}
