package com.xuejiai.aaf.framework.messaging.ws;

/**
 * 用户间即时消息（IM）推送通知。
 *
 * @param conversationId 会话 ID
 * @param messageId 消息 ID
 * @param senderId 发送者 ID（字符串，兼容 HUMAN/AI/STAFF/BOT 等多种参与者类型标识）
 * @param content 消息内容
 * @author AaronZZH & Kiro
 */
public record ImMessage(Long conversationId, Long messageId, String senderId, String content)
        implements WsMessage {

    @Override
    public String type() {
        return "im_message";
    }
}
