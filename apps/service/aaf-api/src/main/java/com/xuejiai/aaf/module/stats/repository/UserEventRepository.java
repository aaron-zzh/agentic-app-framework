package com.xuejiai.aaf.module.stats.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.xuejiai.aaf.module.stats.domain.UserEvent;

/** 用户行为事件仓储。 */
public interface UserEventRepository extends JpaRepository<UserEvent, Long> {

    /** 统计指定时间范围内某事件类型的用户数 */
    @Query(
            "SELECT COUNT(DISTINCT e.userId) FROM UserEvent e WHERE e.eventType = :eventType AND e.createTime BETWEEN :start AND :end")
    long countDistinctUserByEventType(String eventType, LocalDateTime start, LocalDateTime end);
}
