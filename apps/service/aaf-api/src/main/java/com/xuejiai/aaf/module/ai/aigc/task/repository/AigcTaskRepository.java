package com.xuejiai.aaf.module.ai.aigc.task.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.ai.aigc.task.domain.AigcTask;

/**
 * AIGC 统一任务 Repository。
 *
 * @author AaronZZH
 */
public interface AigcTaskRepository extends CrudEntityRepository<AigcTask> {

    /** 按用户分页查询（最新在前） */
    Page<AigcTask> findByUserIdOrderByCreateTimeDesc(Long userId, Pageable pageable);

    /** 按第三方任务 ID 查找。 */
    Optional<AigcTask> findByProviderTaskId(String providerTaskId);

    /** 查询指定状态的任务列表 */
    List<AigcTask> findByStatus(String status);

    /** 查询指定状态且创建时间早于给定时间的任务（用于检测卡住的任务） */
    List<AigcTask> findByStatusAndCreateTimeBefore(String status, LocalDateTime before);

    /** 统计用户在指定时间之后的任务数（用于今日生成统计） */
    long countByUserIdAndCreateTimeAfter(Long userId, LocalDateTime after);

    /** 按状态和任务类型查询 */

    @Query(
            """
            select task from AigcTask task
            where task.status = 'PREPARED'
            order by task.id asc
            """)
    List<AigcTask> findRecoverableIntents();

    @Query(
            """
            select task from AigcTask task
            where task.status = 'SUBMITTING'
              and task.submitLeaseUntil < :now
            order by task.id asc
            """)
    List<AigcTask> findStaleSubmitting(@Param("now") LocalDateTime now);

    @Modifying
    @Query(
            """
            update AigcTask task
               set task.status = 'SUBMITTING',
                   task.submitOwner = :owner,
                   task.submitLeaseUntil = :leaseUntil,
                   task.updateTime = CURRENT_TIMESTAMP,
                   task.version = task.version + 1
             where task.id = :taskId
               and task.status = 'PREPARED'
            """)
    int claimIntent(
            @Param("taskId") Long taskId,
            @Param("owner") String owner,
            @Param("leaseUntil") LocalDateTime leaseUntil);

    List<AigcTask> findByStatusAndType(String status, String type);

    List<AigcTask> findByProjectIdOrderByIdAsc(Long projectId);

    Optional<AigcTask> findByExecutionRunIdAndIdempotencyKey(
            Long executionRunId, String idempotencyKey);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            update AigcTask task
               set task.status = 'COMPLETING',
                   task.updateTime = CURRENT_TIMESTAMP,
                   task.version = task.version + 1
             where task.providerTaskId = :providerTaskId
               and task.type = :taskType
               and task.status in ('PENDING', 'RUNNING')
            """)
    int claimCompletion(
            @Param("providerTaskId") String providerTaskId,
            @Param("taskType") String taskType);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from AigcTask task where task.id = :id")
    Optional<AigcTask> findLockedById(@Param("id") Long id);

    @Modifying
    @Query(
            value =
                    """
                    UPDATE generation_history
                    SET deleted = true, delete_time = CURRENT_TIMESTAMP
                    WHERE deleted = false
                      AND task_id IN (SELECT id FROM aigc_task WHERE project_id = :projectId)
                    """,
            nativeQuery = true)
    int softDeleteGenerationHistoryByProjectId(@Param("projectId") Long projectId);
}
