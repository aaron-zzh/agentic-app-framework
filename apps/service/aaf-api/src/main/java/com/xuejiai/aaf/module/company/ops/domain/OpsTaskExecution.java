package com.xuejiai.aaf.module.company.ops.domain;

import java.time.LocalDateTime;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

/** 运营任务执行记录 */
@Getter
@Setter
@Entity
@Table(name = "company_ops_execution")
@SQLDelete(sql = "UPDATE company_ops_execution SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class OpsTaskExecution extends BaseEntity {

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    /** 状态（PENDING/RUNNING/SUCCESS/FAILED/CANCELLED） */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    /** 执行结果（JSON） */
    @Column(name = "result", columnDefinition = "text")
    private String result;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    /** 触发方式（SCHEDULER/USER/EVENT） */
    @Column(name = "triggered_by", nullable = false, length = 16)
    private String triggeredBy;
}
