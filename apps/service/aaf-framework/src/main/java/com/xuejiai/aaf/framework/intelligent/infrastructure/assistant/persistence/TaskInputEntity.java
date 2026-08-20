package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionInput;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ai_task_input_buffer")
public class TaskInputEntity {
    @Id
    @Column(name = "input_id", length = 128)
    private String inputId;

    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;

    @Column(name = "task_id", nullable = false, length = 128)
    private String taskId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_payload", nullable = false, columnDefinition = "jsonb")
    private ExecutionInput input;
}
