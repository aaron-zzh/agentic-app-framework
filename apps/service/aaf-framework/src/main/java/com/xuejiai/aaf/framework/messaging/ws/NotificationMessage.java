package com.xuejiai.aaf.framework.messaging.ws;

/**
 * 系统通知消息。
 *
 * @param notificationType 业务通知类型（system / approval / mention / task / change 等， 取值见 {@code
 *     NotificationType}）
 * @param title 通知标题
 * @param body 通知正文
 * @param relatedUrl 关联跳转链接，无则传空字符串
 * @author AaronZZH & Kiro
 */
public record NotificationMessage(
        String notificationType, String title, String body, String relatedUrl)
        implements WsMessage {

    @Override
    public String type() {
        return "notification";
    }
}
