package com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.framework.intelligent.assistant.application.AssistantCommand;

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
        name = "ai_task_recovery_command",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "task_id"}))
public class TaskRecoveryCommandEntity {
    @Id
    @Column(name = "command_key", length = 300)
    private String commandKey;

    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;

    @Column(name = "task_id", nullable = false, length = 128)
    private String taskId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "command_payload", nullable = false, columnDefinition = "jsonb")
    private AssistantCommand command;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
