package com.xuejiai.aaf.framework.messaging.internal;

/** 站内信发送器接口，由业务层实现（依赖 Notification 实体和 WebSocket）。 */
public interface InternalMessageSender {

    /** 发送站内信给指定用户 */
    void send(Long userId, String title, String body);
}
