package com.xuejiai.aaf.module.ai.aigc.image.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.module.ai.aigc.image.domain.BatchGenerationTask;
import com.xuejiai.aaf.module.ai.aigc.image.vo.BatchTaskStatus;

/** 批量生成任务仓储。 */
public interface BatchGenerationTaskRepository extends JpaRepository<BatchGenerationTask, Long> {

    List<BatchGenerationTask> findByUserId(Long userId);

    Optional<BatchGenerationTask> findByIdAndUserId(Long id, Long userId);

    List<BatchGenerationTask> findByStatus(BatchTaskStatus status);

    List<BatchGenerationTask> findByProjectIdOrderByIdAsc(Long projectId);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            update BatchGenerationTask task
            set task.status = :targetStatus
            where task.id = :taskId
              and task.deleted = false
              and task.status = :expectedStatus
            """)
    int transitionStatusIfActive(
            @Param("taskId") Long taskId,
            @Param("expectedStatus") BatchTaskStatus expectedStatus,
            @Param("targetStatus") BatchTaskStatus targetStatus);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            update BatchGenerationTask task
            set task.completedCount = task.completedCount + 1
            where task.id = :taskId
              and task.deleted = false
              and task.status = :runningStatus
            """)
    int incrementCompletedIfRunning(
            @Param("taskId") Long taskId, @Param("runningStatus") BatchTaskStatus runningStatus);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            update BatchGenerationTask task
            set task.failedCount = task.failedCount + 1
            where task.id = :taskId
              and task.deleted = false
              and task.status = :runningStatus
            """)
    int incrementFailedIfRunning(
            @Param("taskId") Long taskId, @Param("runningStatus") BatchTaskStatus runningStatus);
}
