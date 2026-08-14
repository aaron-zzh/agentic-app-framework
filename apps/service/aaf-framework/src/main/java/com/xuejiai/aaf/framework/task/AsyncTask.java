package com.xuejiai.aaf.framework.task;

import java.time.LocalDateTime;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.org.OrgIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 通用异步任务主记录。 */
@Getter
@Setter
@OrgIgnore
@Entity(name = "SysAsyncTask")
@Table(name = "sys_async_task")
public class AsyncTask extends BaseEntity {

    /** 与 Redis Stream AsyncTaskMessage.id 一致的稳定业务任务标识。 */
    @Column(name = "task_id", nullable = false, unique = true, length = 64)
    private String taskId;

    @Column(name = "task_type", nullable = false, length = 100)
    private String taskType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AsyncTaskStatus status;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "result", columnDefinition = "TEXT")
    private String result;

    @Column(name = "priority", nullable = false)
    private Short priority;

    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
