package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantTask;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/** Assistant 任务生命周期与恢复点的 JPA 实体。 */
@Getter
@Setter
@Entity
@Table(
        name = "ai_assistant_task_control",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "task_id"}))
public class AssistantTaskControlEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;

    @Column(name = "task_id", nullable = false, length = 128)
    private String taskId;

    @Column(name = "task_status", nullable = false, length = 32)
    private String taskStatus;

    @Column(name = "control_mode", nullable = false, length = 32)
    private String controlMode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "task_payload", nullable = false, columnDefinition = "jsonb")
    private AssistantTask task;

    @Column(name = "fencing_token", nullable = false)
    private Long fencingToken;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
