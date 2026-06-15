package com.xuejiai.aaf.module.ai.chat.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.xuejiai.aaf.module.ai.chat.domain.TaskExecution;

public interface AiTaskExecutionRepository extends JpaRepository<TaskExecution, Long> {

    /** 获取任务的最新执行实例 */
    Optional<TaskExecution> findFirstByTaskIdOrderByAttemptNoDesc(Long taskId);

    /** 获取主执行的所有子执行 */
    List<TaskExecution> findByParentExecutionIdOrderBySubtaskKey(Long parentExecutionId);

    /** CAS 抢占：pending → running */
    @Modifying
    @Query(
            """
            UPDATE TaskExecution e SET e.status = 'running', e.startedAt = CURRENT_TIMESTAMP, e.updateTime = CURRENT_TIMESTAMP
            WHERE e.id = :id AND e.status = 'pending'""")
    int casStart(Long id);

    /** 孤儿回收：running 超时重置为 pending */
    @Modifying
    @Query(
            """
            UPDATE TaskExecution e SET e.status = 'pending', e.updateTime = CURRENT_TIMESTAMP
            WHERE e.status = 'running' AND e.updateTime < :cutoff""")
    int recoverOrphans(LocalDateTime cutoff);

    /** 查找所有 running 且超时的执行（用于恢复） */
    List<TaskExecution> findByStatusAndUpdateTimeBefore(String status, LocalDateTime cutoff);
}
