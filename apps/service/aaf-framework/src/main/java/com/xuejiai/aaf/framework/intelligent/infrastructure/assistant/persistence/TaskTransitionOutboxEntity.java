package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ai_task_transition_outbox")
public class TaskTransitionOutboxEntity {
    @Id
    @Column(name = "outbox_id", length = 128)
    private String outboxId;

    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;

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
}
