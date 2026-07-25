package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskBoard;

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

@Getter
@Setter
@Entity
@Table(name = "ai_task_board", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "task_id"}))
public class TaskBoardEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;
    @Column(name = "task_id", nullable = false, length = 128)
    private String taskId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "board_payload", nullable = false, columnDefinition = "jsonb")
    private TaskBoard board;
    @Column(name = "fencing_token", nullable = false)
    private Long fencingToken;
    @Version
    @Column(name = "lock_version", nullable = false)
    private Long lockVersion;
}
