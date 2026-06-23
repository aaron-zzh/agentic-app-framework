package com.xuejiai.aaf.module.user.growth.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 用户成长任务进度。 */
@Getter
@Setter
@Entity
@Table(
        name = "user_growth_progress",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "task_id"}))
@SQLDelete(
        sql =
                "UPDATE user_growth_progress SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class UserGrowthProgress extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "progress", nullable = false)
    private Integer progress = 0;

    /** PENDING / COMPLETED / CLAIMED */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "completed_time")
    private LocalDateTime completedTime;

    @Column(name = "claimed_time")
    private LocalDateTime claimedTime;
}
