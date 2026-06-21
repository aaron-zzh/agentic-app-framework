package com.xuejiai.aaf.module.stats.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.stats.domain.AigcTaskTrendStats;

/**
 * AIGC 任务趋势统计仓储。
 *
 * @author AaronZZH & Kiro
 */
public interface AigcTaskTrendStatsRepository extends JpaRepository<AigcTaskTrendStats, Long> {

    /** 查询指定类型、时间范围内的趋势数据，按时间升序 */
    List<AigcTaskTrendStats> findByTaskTypeAndTimePeriodBetweenAndDeletedFalseOrderByTimePeriodAsc(
            String taskType, LocalDateTime from, LocalDateTime to);

    /** 删除指定时间之前的过期数据 */
    void deleteByTimePeriodBefore(LocalDateTime cutoff);
}
