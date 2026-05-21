package com.xuejiai.aaf.module.system.task.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.task.domain.TaskExecution;

/** 任务执行记录仓储。 */
public interface TaskExecutionRepository extends JpaRepository<TaskExecution, Long> {

    Page<TaskExecution> findByTaskNameContainingOrTaskTypeContaining(
            String taskName, String taskType, Pageable pageable);

    Page<TaskExecution> findByStatus(String status, Pageable pageable);
}
