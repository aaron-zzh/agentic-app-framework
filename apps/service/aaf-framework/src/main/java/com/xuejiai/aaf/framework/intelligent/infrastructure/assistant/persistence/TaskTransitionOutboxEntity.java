package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;

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
        name = "ai_task_transition_outbox",
        uniqueConstraints = {
            @UniqueConstraint(columnNames = {"outbox_id"}),
            @UniqueConstraint(columnNames = {"event_id"})
        })
public class TaskTransitionOutboxEntity extends AssistantRuntimeEntity {

    @Column(name = "outbox_id", nullable = false, length = 128)
    private String outboxId;

    @Column(name = "task_id", nullable = false, length = 128)
    private String taskId;

    @Column(name = "event_id", nullable = false, length = 128)
    private String eventId;

    @Column(nullable = false, length = 16)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_payload", nullable = false, columnDefinition = "jsonb")
    private ExecutionEvent event;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Version
    @Column(name = "runtime_lock_version", nullable = false)
    private Long runtimeLockVersion;
}
