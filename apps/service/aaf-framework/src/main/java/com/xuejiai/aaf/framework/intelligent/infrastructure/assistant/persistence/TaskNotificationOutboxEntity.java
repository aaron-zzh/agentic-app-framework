package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.framework.intelligent.assistant.port.NotificationPort.Notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "ai_task_notification_outbox",
        uniqueConstraints = @UniqueConstraint(columnNames = {"notification_id"}))
public class TaskNotificationOutboxEntity extends AssistantRuntimeEntity {

    @Column(name = "notification_id", nullable = false, length = 128)
    private String notificationId;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(name = "task_id", nullable = false, length = 128)
    private String taskId;

    @Column(nullable = false, length = 32)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "notification_payload", nullable = false, columnDefinition = "jsonb")
    private Notification notification;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "runtime_lock_version", nullable = false)
    private Long runtimeLockVersion;
}
