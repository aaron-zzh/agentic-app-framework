package com.xuejiai.aaf.framework.intelligent.assistant.port;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/** 委托任务通知边界；实现必须先写 outbox，发送失败不得回滚任务事实。 */
public interface NotificationPort {

    NotificationResult notify(Notification notification);

    int retryFailed(int limit);

    record Notification(
            String notificationId,
            TenantId tenantId,
            UserId userId,
            TaskId taskId,
            Type type,
            String summary,
            Map<String, Object> details,
            Instant createdAt) {
        public Notification {
            if (notificationId == null || notificationId.isBlank()) {
                throw new IllegalArgumentException("notificationId 不能为空白");
            }
            Objects.requireNonNull(tenantId, "tenantId 不能为空");
            Objects.requireNonNull(userId, "userId 不能为空");
            Objects.requireNonNull(taskId, "taskId 不能为空");
            Objects.requireNonNull(type, "type 不能为空");
            if (summary == null || summary.isBlank()) {
                throw new IllegalArgumentException("summary 不能为空白");
            }
            details = details == null ? Map.of() : Map.copyOf(details);
            Objects.requireNonNull(createdAt, "createdAt 不能为空");
        }
    }

    record NotificationResult(String notificationId, boolean created, boolean dispatched) {}

    enum Type {
        STATUS_CHANGED,
        AUTHORIZATION_GAP,
        CONSECUTIVE_FAILURE,
        BUDGET_WARNING,
        COMPLETED
    }
}
