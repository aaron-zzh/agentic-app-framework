package com.xuejiai.aaf.module.system.notify.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.xuejiai.aaf.framework.messaging.internal.InternalMessageSender;
import com.xuejiai.aaf.module.system.notify.domain.Notification;
import com.xuejiai.aaf.module.system.notify.repository.NotificationRepository;
import com.xuejiai.aaf.module.system.notify.ws.WebSocketSessionManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 站内信发送器实现，创建 Notification 记录并通过 WebSocket 实时推送。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InternalMessageSenderImpl implements InternalMessageSender {

    private final NotificationRepository notificationRepository;
    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void send(
            Long userId,
            String type,
            String title,
            String body,
            String relatedUrl,
            String entityType,
            Long entityId) {
        // 持久化通知
        var notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setRelatedUrl(relatedUrl);
        notification.setEntityType(entityType);
        notification.setEntityId(entityId);
        notification.setIsRead(false);
        notificationRepository.save(notification);

        // WebSocket 实时推送（用户不在线时静默忽略）
        try {
            var payload =
                    objectMapper.writeValueAsString(
                            java.util.Map.of(
                                    "type",
                                    "notification",
                                    "notificationType",
                                    type,
                                    "title",
                                    title,
                                    "body",
                                    body != null ? body : "",
                                    "relatedUrl",
                                    relatedUrl != null ? relatedUrl : ""));
            sessionManager.sendToUser(userId, payload);
        } catch (Exception e) {
            log.warn("WebSocket 推送失败，用户 {} 可能不在线", userId);
        }
    }
}
