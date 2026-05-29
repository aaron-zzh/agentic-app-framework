package com.xuejiai.aaf.module.ai.chat.domain;

import java.time.LocalDateTime;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 任务执行实例——一个 ChatTask 可多次执行（重试），支持主/子关系（多实例协调）。
 */
@Getter
@Setter
@Entity
@Table(name = "ai_task_execution")
public class TaskExecution extends BaseEntity {

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    /** 子任务指向主执行实例（NULL=主执行） */
    @Column(name = "parent_execution_id")
    private Long parentExecutionId;

    /** 子任务标识（如 backend/frontend） */
    @Column(name = "subtask_key", length = 100)
    private String subtaskKey;

    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo = 1;

    /** pending/running/done/failed/cancelled/waiting_approval */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "pending";

    @Column(name = "role", length = 100)
    private String role;

    /** 最新检查点 ID */
    @Column(name = "checkpoint_id")
    private Long checkpointId;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}
