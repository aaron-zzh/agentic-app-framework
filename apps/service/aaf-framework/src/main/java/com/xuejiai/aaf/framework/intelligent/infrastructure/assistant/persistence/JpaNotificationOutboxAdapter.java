package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;

import com.xuejiai.aaf.framework.intelligent.assistant.port.NotificationPort;

/** 通知 outbox 实现；任务事务不依赖通知投递结果。 */
public final class JpaNotificationOutboxAdapter implements NotificationPort {
    private final TaskNotificationOutboxRepository repository;
    private final ApplicationEventPublisher publisher;

    public JpaNotificationOutboxAdapter(
            TaskNotificationOutboxRepository repository, ApplicationEventPublisher publisher) {
        this.repository = Objects.requireNonNull(repository, "repository 不能为空");
        this.publisher = Objects.requireNonNull(publisher, "publisher 不能为空");
    }

    @Override
    public NotificationResult notify(Notification notification) {
        try {
            return persistAndDispatch(notification);
        } catch (RuntimeException ignored) {
            return new NotificationResult(notification.notificationId(), false, false);
        }
    }

    private NotificationResult persistAndDispatch(Notification notification) {
        var existing = repository.findByNotificationId(notification.notificationId()).orElse(null);
        if (existing != null) {
            return new NotificationResult(
                    notification.notificationId(),
                    false,
                    "DISPATCHED".equals(existing.getStatus()));
        }
        var entity = new TaskNotificationOutboxEntity();
        entity.setNotificationId(notification.notificationId());
        entity.setTenantId(notification.tenantId().value());
        entity.setUserId(notification.userId().value());
        entity.setTaskId(notification.taskId().value());
        entity.setStatus("PENDING");
        entity.setNotification(notification);
        entity.setCreatedAt(notification.createdAt());
        entity.setUpdatedAt(notification.createdAt());
        repository.saveAndFlush(entity);
        return dispatch(entity, true);
    }

    @Override
    public int retryFailed(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("通知重投 limit 必须在 1..100");
        }
        var pending =
                repository.findByStatusInOrderByUpdatedAtAsc(
                        List.of("PENDING", "FAILED"), PageRequest.of(0, limit));
        pending.forEach(entity -> dispatch(entity, false));
        return pending.size();
    }

    private NotificationResult dispatch(TaskNotificationOutboxEntity entity, boolean created) {
        try {
            publisher.publishEvent(entity.getNotification());
            entity.setStatus("DISPATCHED");
            entity.setLastError(null);
            entity.setUpdatedAt(Instant.now());
            repository.saveAndFlush(entity);
            return new NotificationResult(entity.getNotificationId(), created, true);
        } catch (RuntimeException failure) {
            entity.setStatus("FAILED");
            entity.setLastError(truncate(failure.getMessage()));
            entity.setUpdatedAt(Instant.now());
            repository.saveAndFlush(entity);
            return new NotificationResult(entity.getNotificationId(), created, false);
        }
    }

    private static String truncate(String message) {
        var value = Objects.requireNonNullElse(message, "通知投递失败");
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
