package com.xuejiai.aaf.framework.intelligent.infrastructure.trace.persistence;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "ai_task_event",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "execution_id", "sequence"}))
public class ExecutionEventEntity {
    @Id
    @Column(name = "event_id", length = 128)
    private String eventId;
    @Column(name = "event_offset", nullable = false, insertable = false, updatable = false)
    private Long eventOffset;
    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;
    @Column(name = "task_id", nullable = false, length = 128)
    private String taskId;
    @Column(name = "execution_id", nullable = false, length = 128)
    private String executionId;
    @Column(nullable = false)
    private Long sequence;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_payload", nullable = false, columnDefinition = "jsonb")
    private ExecutionEvent event;
}
