package com.xuejiai.aaf.framework.task;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;

import jakarta.persistence.LockModeType;

/** 通用异步任务仓储。 */
public interface AsyncTaskRepository extends CrudEntityRepository<AsyncTask> {

    Optional<AsyncTask> findByTaskId(String taskId);

    boolean existsByTaskId(String taskId);

    Optional<AsyncTask> findByTaskIdAndOwnerId(String taskId, Long ownerId);

    Page<AsyncTask> findByOwnerId(Long ownerId, Pageable pageable);

    Page<AsyncTask> findByOwnerIdAndStatus(Long ownerId, AsyncTaskStatus status, Pageable pageable);

    Page<AsyncTask> findByStatus(AsyncTaskStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "SELECT task FROM SysAsyncTask task "
                    + "WHERE task.status = :status ORDER BY task.createTime ASC")
    List<AsyncTask> findByStatusForUpdate(
            @Param("status") AsyncTaskStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT task FROM SysAsyncTask task WHERE task.taskId = :taskId")
    Optional<AsyncTask> findByTaskIdForUpdate(@Param("taskId") String taskId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            "DELETE FROM SysAsyncTask task "
                    + "WHERE task.status IN :statuses AND task.completedAt < :completedBefore")
    int deleteCompletedBefore(
            @Param("statuses") Set<AsyncTaskStatus> statuses,
            @Param("completedBefore") LocalDateTime completedBefore);
}
