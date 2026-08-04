package com.xuejiai.aaf.module.ai.aigc.execution.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** ExecutionRun 与媒体子任务的内部关联，不开放独立 CRUD。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_execution_task_ref")
public class AigcExecutionTaskRef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "execution_run_id", nullable = false)
    private Long executionRunId;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(nullable = false, length = 32)
    private String role;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime = LocalDateTime.now();

    @Column(nullable = false)
    private Boolean deleted = false;

    @Column(name = "delete_time")
    private LocalDateTime deleteTime;
}
