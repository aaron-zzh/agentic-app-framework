package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionInput;

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
        name = "ai_task_input_buffer",
        uniqueConstraints = @UniqueConstraint(columnNames = {"org_id", "input_id"}))
public class TaskInputEntity extends AssistantRuntimeEntity {

    @Column(name = "input_id", nullable = false, length = 128)
    private String inputId;

    @Column(name = "task_id", nullable = false, length = 128)
    private String taskId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_payload", nullable = false, columnDefinition = "jsonb")
    private ExecutionInput input;

    @Version
    @Column(name = "runtime_lock_version", nullable = false)
    private Long runtimeLockVersion;
}
