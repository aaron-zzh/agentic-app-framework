package com.xuejiai.aaf.module.system.notify.service;

import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.messaging.internal.InternalMessage;
import com.xuejiai.aaf.framework.messaging.internal.InternalMessage.PushStrategy;
import com.xuejiai.aaf.framework.messaging.internal.InternalMessageSender;
import com.xuejiai.aaf.framework.messaging.ws.NotificationMessage;
import com.xuejiai.aaf.framework.messaging.ws.WebSocketMessageSender;
import com.xuejiai.aaf.module.system.notify.domain.Notification;
import com.xuejiai.aaf.module.system.notify.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 站内信发送器实现。
 *
 * <p>根据消息类型（{@code type}）自动选择推送策略：
 *
 * <ul>
 *   <li>{@code PERSIST_ONLY}：只存库，不实时推送（批量通知场景）
 *   <li>{@code WS_ONLY}：存库 + WebSocket 推送
 *   <li>{@code ALL}（默认）：存库 + WebSocket 推送
 * </ul>
 *
 * <p>新增业务类型时，在 {@link #PUSH_STRATEGY} 中注册对应策略即可，调用方无需改动。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InternalMessageSenderImpl implements InternalMessageSender {

    private final NotificationRepository notificationRepository;
    private final WebSocketMessageSender wsMessageSender;

    /** 消息类型 → 推送策略映射表。 未注册的类型默认走 ALL。 */
    private static final Map<String, PushStrategy> PUSH_STRATEGY =
            Map.of(
                    "approval", PushStrategy.ALL, // 审批通知：重要，存库 + WS
                    "mention", PushStrategy.ALL, // @提及：重要，存库 + WS
                    "task", PushStrategy.ALL, // 任务通知：重要，存库 + WS
                    "system", PushStrategy.WS_ONLY, // 系统通知：存库 + WS
                    "change", PushStrategy.WS_ONLY, // 变更通知：存库 + WS
                    "batch", PushStrategy.PERSIST_ONLY // 批量通知：只存库，不实时推
                    );

    @Override
    @Transactional
    public void send(InternalMessage message) {
        var strategy = resolveStrategy(message);

        var notification = new Notification();
        notification.setUserId(message.getUserId());
        notification.setType(message.getType());
        notification.setTitle(message.getTitle());
        notification.setBody(message.getBody());
        notification.setRelatedUrl(message.getRelatedUrl());
        notification.setEntityType(message.getEntityType());
        notification.setEntityId(message.getEntityId());
        notification.setIsRead(false);
        notificationRepository.save(notification);

        if (strategy == PushStrategy.PERSIST_ONLY) return;

        try {
            var notificationMessage =
                    new NotificationMessage(
                            message.getType(),
                            message.getTitle(),
                            message.getBody() != null ? message.getBody() : "",
                            message.getRelatedUrl() != null ? message.getRelatedUrl() : "");
            wsMessageSender.send(message.getUserId(), notificationMessage);
        } catch (Exception e) {
            log.warn("消息推送失败，userId={} type={}", message.getUserId(), message.getType(), e);
        }
    }

    /** 调用方指定策略优先，否则按 type 默认 */
    private PushStrategy resolveStrategy(InternalMessage message) {
        if (message.getStrategy() != null) return message.getStrategy();
        return PUSH_STRATEGY.getOrDefault(message.getType(), PushStrategy.ALL);
    }
}
