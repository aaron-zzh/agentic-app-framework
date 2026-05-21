package com.xuejiai.aaf.module.system.task.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 计划任务。 */
@Getter
@Setter
@Entity
@Table(name = "sys_scheduled_task")
@SQLDelete(
        sql =
                "UPDATE sys_scheduled_task SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class ScheduledTask extends BaseEntity {

    /** 任务名称 */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** 任务类型：trash_cleanup/archive/automation_schedule */
    @Column(name = "type", nullable = false, length = 50)
    private String type;

    /** Cron 表达式 */
    @Column(name = "cron", nullable = false, length = 50)
    private String cron;

    /** 状态：active/paused/failed */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "active";

    /** 上次执行时间 */
    @Column(name = "last_run")
    private LocalDateTime lastRun;

    /** 下次执行时间 */
    @Column(name = "next_run")
    private LocalDateTime nextRun;

    /** 连续失败次数 */
    @Column(name = "fail_count", nullable = false)
    private Integer failCount = 0;
}
