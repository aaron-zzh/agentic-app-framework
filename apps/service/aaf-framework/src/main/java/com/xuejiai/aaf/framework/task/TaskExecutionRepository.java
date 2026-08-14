package com.xuejiai.aaf.framework.task;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;

/**
 * 任务执行记录仓储。
 *
 * @author AaronZZH & Kiro
 */
public interface TaskExecutionRepository extends CrudEntityRepository<TaskExecution> {

    Page<TaskExecution> findByTaskNameContainingOrTaskTypeContaining(
            String taskName, String taskType, Pageable pageable);

    Page<TaskExecution> findByStatus(String status, Pageable pageable);
}
