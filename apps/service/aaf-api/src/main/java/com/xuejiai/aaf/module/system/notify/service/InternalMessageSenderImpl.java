package com.xuejiai.aaf.module.system.notify.service;

import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.xuejiai.aaf.framework.messaging.internal.InternalMessage;
import com.xuejiai.aaf.framework.messaging.internal.InternalMessage.PushStrategy;
import com.xuejiai.aaf.framework.messaging.internal.InternalMessageSender;
import com.xuejiai.aaf.framework.messaging.sse.SseSessionManager;
import com.xuejiai.aaf.framework.messaging.ws.WebSocketSessionManager;
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
 *   <li>{@code SSE_ONLY}：不存库 + SSE 推送（进度场景，高频更新）
 *   <li>{@code ALL}（默认）：存库 + WS + SSE
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
    private final WebSocketSessionManager wsSessionManager;
    private final SseSessionManager sseSessionManager;
    private final ObjectMapper objectMapper;

    /** 消息类型 → 推送策略映射表。 未注册的类型默认走 ALL。 */
    private static final Map<String, PushStrategy> PUSH_STRATEGY =
            Map.of(
                    "approval", PushStrategy.ALL, // 审批通知：重要，全渠道
                    "mention", PushStrategy.ALL, // @提及：重要，全渠道
                    "task", PushStrategy.ALL, // 任务通知：重要，全渠道
                    "system", PushStrategy.WS_ONLY, // 系统通知：存库 + WS，SSE 不必要
                    "change", PushStrategy.WS_ONLY, // 变更通知：存库 + WS
                    "progress", PushStrategy.SSE_ONLY, // 进度推送：高频，不存库，只 SSE
                    "batch", PushStrategy.PERSIST_ONLY // 批量通知：只存库，不实时推
                    );

    /** 只走 SSE 的类型集合（不需要存库） */
    private static final Set<String> NO_PERSIST_TYPES = Set.of("progress");

    @Override
    @Transactional
    public void send(InternalMessage message) {
        var strategy = resolveStrategy(message);

        // 持久化（SSE_ONLY 不存库）
        if (strategy != PushStrategy.SSE_ONLY) {
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
        }

        if (strategy == PushStrategy.PERSIST_ONLY) return;

        try {
            var payload =
                    objectMapper.writeValueAsString(
                            java.util.Map.of(
                                    "type",
                                    "notification",
                                    "notificationType",
                                    message.getType(),
                                    "title",
                                    message.getTitle(),
                                    "body",
                                    message.getBody() != null ? message.getBody() : "",
                                    "relatedUrl",
                                    message.getRelatedUrl() != null
                                            ? message.getRelatedUrl()
                                            : ""));

            if (strategy == PushStrategy.WS_ONLY || strategy == PushStrategy.ALL) {
                wsSessionManager.sendToUser(message.getUserId(), payload);
            }
            if ((strategy == PushStrategy.SSE_ONLY || strategy == PushStrategy.ALL)
                    && sseSessionManager.hasSubscriber(message.getUserId())) {
                sseSessionManager.push(message.getUserId(), "notification", payload);
            }
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
