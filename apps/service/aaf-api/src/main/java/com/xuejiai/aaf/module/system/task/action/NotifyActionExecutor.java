package com.xuejiai.aaf.module.system.task.action;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.module.system.notify.service.NotificationService;
import com.xuejiai.aaf.module.system.task.domain.ScheduledTask;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 发送通知动作。 actionConfig JSON: {"userId": 1, "title": "标题", "content": "内容"} */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotifyActionExecutor implements ScheduledActionExecutor {

    private final NotificationService notificationService;

    @Override
    public String actionType() {
        return "NOTIFY";
    }

    @Override
    public void execute(ScheduledTask task) {
        try {
            var config = JsonUtils.readTree(task.getActionConfig());
            var userId = config.get("userId").asLong();
            var title = config.get("title").asText();
            var content = config.get("content").asText();
            notificationService.sendSystemNotification(userId, title, content);
        } catch (Exception e) {
            log.error("NOTIFY 动作执行失败，taskId={}", task.getId(), e);
            throw new RuntimeException("NOTIFY 动作执行失败: " + e.getMessage(), e);
        }
    }
}
