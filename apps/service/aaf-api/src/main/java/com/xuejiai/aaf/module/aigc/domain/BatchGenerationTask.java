package com.xuejiai.aaf.module.aigc.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.module.aigc.enums.BatchTaskStatus;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 批量生成任务实体。 */
@Getter
@Setter
@Entity
@Table(name = "batch_generation_task")
@SQLDelete(sql = "UPDATE batch_generation_task SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class BatchGenerationTask extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BatchTaskStatus status = BatchTaskStatus.PENDING;

    @Column(name = "total_count", nullable = false)
    private Integer totalCount = 0;

    @Column(name = "completed_count", nullable = false)
    private Integer completedCount = 0;

    @Column(name = "failed_count", nullable = false)
    private Integer failedCount = 0;

    /** 批量生成参数（JSON） */
    @Column(name = "params", columnDefinition = "JSONB")
    private String params;
}
