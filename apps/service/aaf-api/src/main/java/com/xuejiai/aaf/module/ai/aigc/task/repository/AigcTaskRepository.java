package com.xuejiai.aaf.module.ai.aigc.task.repository;

import java.time.LocalDateTime;
import java.util.Collection;
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

    List<AigcTask> findByProjectIdAndUserIdOrderByIdAsc(Long projectId, Long userId);

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
            @Param("providerTaskId") String providerTaskId, @Param("taskType") String taskType);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from AigcTask task where task.id = :id")
    Optional<AigcTask> findLockedById(@Param("id") Long id);

    /**
     * 以事务级 advisory lock 串行化同一用户的并发窗口准入。
     *
     * <p>并发窗口是"同时最多几个"的瞬时上限，判定必须是"数占槽任务 + 认领"的原子序列，否则多实例会同时读到同一个空位并各自放行。 使用 try
     * 版本而非阻塞版本：抢不到锁说明该用户已有另一次准入正在进行，本次直接放弃，队列由那次准入或恢复扫描继续推进，不占用线程等待。
     */
    @Query(
            value =
                    """
                    SELECT pg_try_advisory_xact_lock(
                        hashtextextended('aigc-task-admission:' || cast(:userId as text), 0))
                    """,
            nativeQuery = true)
    boolean tryLockUserAdmission(@Param("userId") Long userId);

    /** 统计用户当前占用并发槽位的任务数；状态集合由 {@code AigcTaskStatusEnum} 唯一定义。 */
    long countByUserIdAndStatusInAndDeletedFalse(Long userId, Collection<String> statuses);

    /** 取用户最早的排队任务；排队顺序按持久化顺序（id 升序），不按优先级重排。 */
    Optional<AigcTask> findFirstByUserIdAndStatusAndDeletedFalseOrderByIdAsc(
            Long userId, String status);

    /** 只读取排队中任务的归属用户，供准入判定使用；非 PREPARED 时返回空。 */
    @Query(
            """
            select task.userId from AigcTask task
            where task.id = :taskId
              and task.status = 'PREPARED'
              and task.deleted = false
            """)
    Optional<Long> findQueuedIntentUserId(@Param("taskId") Long taskId);

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
