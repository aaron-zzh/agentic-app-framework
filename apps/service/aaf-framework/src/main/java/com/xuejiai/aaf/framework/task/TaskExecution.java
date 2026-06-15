package com.xuejiai.aaf.framework.task;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 任务执行记录——定时任务和队列任务的通用执行历史。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity(name = "SysTaskExecution")
@Table(name = "sys_task_execution")
public class TaskExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_name", nullable = false, length = 100)
    private String taskName;

    @Column(name = "task_type", nullable = false, length = 50)
    private String taskType;

    /** running / success / failed / timeout */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "running";

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    /** 队列任务优先级（0-9，0 最高），定时任务为 null */
    @Column(name = "priority")
    private Short priority;

    /** 定时任务触发类型（CRON/FIXED_DELAY/FIXED_RATE），队列任务为 null */
    @Column(name = "trigger_type", length = 20)
    private String triggerType;

    @Column(name = "biz_id", length = 100)
    private String bizId;

    @Column(name = "context", columnDefinition = "TEXT")
    private String context;

    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;
}
