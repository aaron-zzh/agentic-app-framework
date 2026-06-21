package com.xuejiai.aaf.module.stats.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * AIGC 任务完成趋势统计缓存（5 分钟粒度）。
 *
 * <p>由定时任务每 5 分钟调用 PG 函数 {@code refresh_aigc_task_trend_stats()} 写入， 供 Dashboard 趋势图直接查询，避免实时扫描
 * aigc_task 明细表。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(
        name = "aigc_task_trend_stats",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_aigc_task_trend_unique",
                        columnNames = {"time_period", "task_type"}))
@SQLDelete(
        sql =
                "UPDATE aigc_task_trend_stats SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AigcTaskTrendStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 5 分钟时间段起始点 */
    @Column(name = "time_period", nullable = false)
    private LocalDateTime timePeriod;

    /** 任务类型：IMAGE / VIDEO / MODEL_3D / MUSIC / VOICE / ALL */
    @Column(name = "task_type", nullable = false, length = 20)
    private String taskType;

    /** 该 5 分钟内完成（SUCCESS）任务数 */
    @Column(name = "period_count", nullable = false)
    private Integer periodCount = 0;

    /** 截止该时间段的累计完成数 */
    @Column(name = "cumulative_count", nullable = false)
    private Integer cumulativeCount = 0;

    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime = LocalDateTime.now();

    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime = LocalDateTime.now();

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;

    @Column(name = "delete_time")
    private LocalDateTime deleteTime;
}
