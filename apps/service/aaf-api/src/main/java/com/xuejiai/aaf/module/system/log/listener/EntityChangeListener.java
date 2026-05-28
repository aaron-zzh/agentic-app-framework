package com.xuejiai.aaf.module.system.log.listener;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.module.system.log.event.EntityChangeEvent;
import com.xuejiai.aaf.module.system.log.service.ActivityService;
import com.xuejiai.aaf.module.system.log.service.AuditLogService;
import com.xuejiai.aaf.module.system.notify.domain.Notification;
import com.xuejiai.aaf.module.system.notify.repository.NotificationRepository;
import com.xuejiai.aaf.module.system.notify.service.SubscriptionService;
import com.xuejiai.aaf.module.system.notify.ws.WebSocketSessionManager;
import com.xuejiai.aaf.module.system.workflow.service.AutomationService;

import lombok.RequiredArgsConstructor;

/**
 * 监听实体变更事件，自动记录活动日志、审计日志并通知订阅者。
 *
 * @author AaronZZH & Kiro
 */
@Component
@RequiredArgsConstructor
public class EntityChangeListener {

    private final ActivityService activityService;
    private final AuditLogService auditLogService;
    private final SubscriptionService subscriptionService;
    private final NotificationRepository notificationRepository;
    private final WebSocketSessionManager webSocketSessionManager;
    private final AutomationService automationService;

    @EventListener
    public void onEntityChange(EntityChangeEvent event) {
        // 记录活动日志
        activityService.record(
                event.entityType(), event.entityId(), event.action(), event.changes());

        // 记录审计日志
        auditLogService.record(
                event.entityType(), event.entityId(), event.action(), event.changes());

        // 查询订阅者并生成通知
        var subscribers = subscriptionService.findSubscribers(event.entityType(), event.entityId());
        for (var sub : subscribers) {
            var notification = new Notification();
            notification.setUserId(sub.getUserId());
            notification.setType("subscription");
            notification.setTitle("字段变更通知");
            notification.setBody(
                    event.entityType() + " #" + event.entityId() + " " + event.action());
            notification.setEntityType(event.entityType());
            notification.setEntityId(event.entityId());
            notificationRepository.save(notification);

            // 实时推送
            webSocketSessionManager.sendToUser(
                    sub.getUserId(),
                    "{\"type\":\"subscription\",\"entityType\":\"%s\",\"entityId\":%d}"
                            .formatted(event.entityType(), event.entityId()));
        }

        // 触发自动化规则
        var triggerType =
                switch (event.action()) {
                    case "create" -> "on_create";
                    case "update" -> "on_update";
                    default -> null;
                };
        if (triggerType != null) {
            automationService.trigger(event, triggerType);
        }
        // field_change 触发（changes 非空时）
        if (event.changes() != null && !event.changes().isEmpty()) {
            automationService.trigger(event, "field_change");
        }
    }
}
