package com.xuejiai.aaf.module.ai.aigc.project.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectSchedule;

import jakarta.persistence.LockModeType;

/**
 * AIGC 项目定时任务仓储。
 *
 * @author Kiro
 */
public interface AigcProjectScheduleRepository extends JpaRepository<AigcProjectSchedule, Long> {

    /**
     * 查询项目的所有定时任务。
     *
     * @param projectId 项目 ID
     * @return 定时任务列表
     */
    List<AigcProjectSchedule> findByProjectIdAndDeletedFalseOrderByExecuteAtAsc(Long projectId);

    /**
     * 查询用户的所有定时任务。
     *
     * @param ownerId 用户 ID
     * @return 定时任务列表
     */
    List<AigcProjectSchedule> findByOwnerIdAndDeletedFalseOrderByExecuteAtDesc(Long ownerId);

    /**
     * 原子认领 due 记录（FOR UPDATE SKIP LOCKED）。
     *
     * <p>扫描器使用此方法原子认领到期的待触发任务。
     *
     * @param now 当前时间
     * @param limit 最多认领数量
     * @return 认领的任务列表
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            value =
                    """
            SELECT s FROM AigcProjectSchedule s
            WHERE s.deleted = false
              AND s.status IN ('PENDING', 'DEFERRED')
              AND (s.nextAttemptAt IS NULL OR s.nextAttemptAt <= :now)
              AND s.executeAt <= :now
            ORDER BY s.executeAt ASC
            LIMIT :limit
            """)
    List<AigcProjectSchedule> claimDueSchedules(@Param("now") Instant now, @Param("limit") int limit);

    /**
     * 查询项目当前是否有活跃的 canonical Task。
     *
     * <p>用于判断是否需要 DEFERRED。
     *
     * @param projectId 项目 ID
     * @return 活跃任务数量
     */
    @Query(
            value =
                    """
            SELECT COUNT(*)
            FROM assistant_task_root t
            WHERE t.project_id = :projectId
              AND t.status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED')
            """,
            nativeQuery = true)
    long countActiveTasksByProject(@Param("projectId") Long projectId);

    /**
     * 按 ID 查询（带乐观锁）。
     *
     * @param id 主键
     * @return 定时任务
     */
    Optional<AigcProjectSchedule> findByIdAndDeletedFalse(Long id);
}
