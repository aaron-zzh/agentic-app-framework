package com.xuejiai.aaf.module.system.notify.service;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.xuejiai.aaf.framework.messaging.internal.InternalMessageSender;
import com.xuejiai.aaf.module.system.log.domain.domain.Notification;
import com.xuejiai.aaf.module.system.notify.repository.NotificationRepository;
import com.xuejiai.aaf.module.system.notify.ws.WebSocketSessionManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 站内信发送器实现，创建 Notification 记录并通过 WebSocket 实时推送。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InternalMessageSenderImpl implements InternalMessageSender {

    private final NotificationRepository notificationRepository;
    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    @Override
    public void send(Long userId, String title, String body) {
        // 持久化通知
        var notification = new Notification();
        notification.setUserId(userId);
        notification.setType("message");
        notification.setTitle(title);
        notification.setBody(body);
        notification.setIsRead(false);
        notificationRepository.save(notification);

        // WebSocket 实时推送
        try {
            var payload =
                    objectMapper.writeValueAsString(
                            java.util.Map.of("type", "notification", "title", title, "body", body));
            sessionManager.sendToUser(userId, payload);
        } catch (Exception e) {
            log.warn("WebSocket 推送失败，用户 {} 可能不在线", userId);
        }
    }
}
