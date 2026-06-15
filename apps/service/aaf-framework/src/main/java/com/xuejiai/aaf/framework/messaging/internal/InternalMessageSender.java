package com.xuejiai.aaf.framework.messaging.internal;

/**
 * 站内信发送器接口，由业务层实现（依赖 Notification 实体、WebSocket、SSE）。
 *
 * <p>推送策略由 {@link InternalMessage#strategy()} 指定，null 时按 type 默认策略。
 */
public interface InternalMessageSender {

    /**
     * 发送站内信。
     *
     * @param message 消息请求，含推送策略
     */
    void send(InternalMessage message);
}
